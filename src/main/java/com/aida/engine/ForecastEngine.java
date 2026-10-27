package com.aida.engine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import com.aida.contract.Absence;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Statuses;

/** Monte Carlo delivery forecast. Pure and stateless. Signatures FROZEN; implemented by Person B in M3 (B2). */
public class ForecastEngine {

    public static final int DEFAULT_RUNS = 10_000;
    public static final long DEFAULT_SEED = 42L;

    /** A run that has not finished after this many weeks stops there. */
    static final int MAX_WEEKS = 104;

    /**
     * @param targetDate nullable; pByTarget is null without one
     */
    public Forecast forecast(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, LocalDate targetDate, int runs, long seed) {
        return forecast(issues, release, asOf, team, absences, targetDate, runs, seed, 1.0);
    }

    /** {@link #forecast(List, String, LocalDate, List, List, LocalDate, int, long)} with 10,000 runs and seed 42. */
    public Forecast forecast(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, LocalDate targetDate) {
        return forecast(issues, release, asOf, team, absences, targetDate, DEFAULT_RUNS, DEFAULT_SEED);
    }

    /**
     * The forecast with every historical throughput sample multiplied by {@code throughputScale}
     * (used by what-ifs that change the team size).
     */
    Forecast forecast(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, LocalDate targetDate, int runs, long seed, double throughputScale) {
        if (runs <= 0) {
            throw new IllegalArgumentException("runs must be positive: " + runs);
        }
        List<Issue> snapshot = Snapshots.snapshot(issues, asOf);
        int remaining = (int) snapshot.stream()
                .filter(i -> release.equals(i.fixVersion()) && !Statuses.DONE.equals(i.status()))
                .count();
        double[] samples = throughputSamples(snapshot, asOf);
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= throughputScale;
        }
        double[] capacity = Capacity.byWeek(team, absences, asOf, MAX_WEEKS);

        SplittableRandom random = new SplittableRandom(seed);
        long[] finish = new long[runs];
        for (int r = 0; r < runs; r++) {
            finish[r] = simulate(remaining, samples, capacity, random);
        }
        Arrays.sort(finish);

        LocalDate p50 = asOf.plusDays(percentile(finish, 0.50));
        LocalDate p85 = asOf.plusDays(percentile(finish, 0.85));
        LocalDate p95 = asOf.plusDays(percentile(finish, 0.95));
        Double pByTarget = null;
        if (targetDate != null) {
            long limit = ChronoUnit.DAYS.between(asOf, targetDate);
            long onTime = Arrays.stream(finish).filter(f -> f <= limit).count();
            pByTarget = (double) onTime / runs;
        }
        int weeksToP85 = (int) Math.min(MAX_WEEKS - 1, ChronoUnit.DAYS.between(asOf, p85) / 7);
        double capacityFactor = round3(Arrays.stream(capacity, 0, weeksToP85 + 1).average().orElse(1.0));

        return new Forecast(projectOf(snapshot, release), release, asOf, remaining, p50, p85, p95, targetDate,
                pByTarget, samples.length, EngineDates.windowStart(asOf), EngineDates.windowEnd(asOf),
                capacityFactor, runs, seed);
    }

    /**
     * Items moved to Done per ISO week over the 12 weeks before asOf's week, oldest first. Weeks that ended before
     * the earliest issue was created are not history and are left out.
     */
    static double[] throughputSamples(List<Issue> snapshot, LocalDate asOf) {
        LocalDate start = EngineDates.windowStart(asOf);
        LocalDate end = EngineDates.windowEnd(asOf);
        LocalDate firstCreated = snapshot.stream().map(Issue::created).min(LocalDate::compareTo).orElse(null);
        if (firstCreated == null || firstCreated.isAfter(end)) {
            return new double[0];
        }
        int firstWeek = firstCreated.isBefore(start) ? 0 : (int) (ChronoUnit.DAYS.between(start, firstCreated) / 7);
        double[] counts = new double[EngineDates.SAMPLE_WEEKS];
        for (Issue issue : snapshot) {
            LocalDate resolved = issue.resolved();
            if (resolved != null && !resolved.isBefore(start) && !resolved.isAfter(end)) {
                counts[(int) (ChronoUnit.DAYS.between(start, resolved) / 7)]++;
            }
        }
        return Arrays.copyOfRange(counts, firstWeek, counts.length);
    }

    /** Days from asOf until this run's remaining work reaches 0, walking week by week. */
    private static long simulate(int remaining, double[] samples, double[] capacity, SplittableRandom random) {
        if (remaining == 0) {
            return 0;
        }
        double left = remaining;
        for (int w = 0; w < MAX_WEEKS; w++) {
            double done = samples.length == 0 ? 0 : samples[random.nextInt(samples.length)] * capacity[w];
            if (done > 0 && done >= left) {
                long dayInWeek = Math.max(0, (long) Math.ceil(left / done * 7) - 1);
                return 7L * w + dayInWeek;
            }
            left -= done;
        }
        return 7L * MAX_WEEKS;
    }

    /** Nearest-rank percentile of sorted values. */
    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static String projectOf(List<Issue> snapshot, String release) {
        return snapshot.stream().filter(i -> release.equals(i.fixVersion())).map(Issue::project).findFirst()
                .orElse(snapshot.isEmpty() ? null : snapshot.get(0).project());
    }

    static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
