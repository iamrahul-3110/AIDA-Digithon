package com.aida.engine;

import com.aida.contract.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Monte Carlo delivery forecast engine.
 * All randomness uses SplittableRandom with a fixed seed for reproducibility.
 */
public class ForecastEngine {

    private ForecastEngine() {}

    public static Forecast forecast(
            List<Issue> allIssues,
            String release,
            LocalDate asOf,
            List<Person> team,
            List<Absence> absences,
            LocalDate targetDate,
            int runs,
            long seed) {

        List<Issue> snap = Snapshots.snapshot(allIssues, asOf);
        List<Issue> releaseIssues = snap.stream()
            .filter(i -> release.equals(i.fixVersion()))
            .collect(Collectors.toList());

        long remaining = releaseIssues.stream().filter(i -> !"Done".equals(i.status())).count();

        // Compute weekly throughput over 12 weeks before asOf
        LocalDate windowEnd = asOf;
        LocalDate windowStart = asOf.minusWeeks(12);
        List<Double> weeklyThroughput = computeWeeklyThroughput(allIssues, release, windowStart, windowEnd);

        if (weeklyThroughput.isEmpty()) {
            // no history: return a degenerate forecast
            return new Forecast(
                getProject(releaseIssues, release), release, asOf,
                (int) remaining, null, null, null, targetDate, null,
                0, windowStart, windowEnd, 1.0, runs, seed
            );
        }

        double[] throughputArr = weeklyThroughput.stream().mapToDouble(Double::doubleValue).toArray();

        // Run Monte Carlo
        SplittableRandom rng = new SplittableRandom(seed);
        long[] finishEpochDays = new long[runs];
        LocalDate simStart = nextMonday(asOf);

        // precompute capacity for enough weeks (up to 3 years)
        int maxWeeks = 156;
        double[] capacity = Capacity.byWeek(team, absences, simStart, maxWeeks);

        for (int r = 0; r < runs; r++) {
            double rem = remaining;
            int week = 0;
            LocalDate weekDate = simStart;
            while (rem > 0 && week < maxWeeks) {
                double cap = capacity[week];
                double tp = throughputArr[rng.nextInt(throughputArr.length)] * cap;
                if (tp >= rem) {
                    // finished during this week – linear interpolation
                    double fraction = (tp > 0) ? rem / tp : 1.0;
                    long daysIntoWeek = Math.round(fraction * 5);
                    weekDate = addWorkdays(weekDate, (int) daysIntoWeek);
                    rem = 0;
                } else {
                    rem -= tp;
                    week++;
                    weekDate = weekDate.plusWeeks(1);
                }
            }
            if (rem > 0) weekDate = simStart.plusWeeks(maxWeeks); // cap
            finishEpochDays[r] = weekDate.toEpochDay();
        }

        Arrays.sort(finishEpochDays);
        LocalDate p50 = LocalDate.ofEpochDay(percentileLong(finishEpochDays, 0.50));
        LocalDate p85 = LocalDate.ofEpochDay(percentileLong(finishEpochDays, 0.85));
        LocalDate p95 = LocalDate.ofEpochDay(percentileLong(finishEpochDays, 0.95));

        Double pByTarget = null;
        if (targetDate != null) {
            long tEpoch = targetDate.toEpochDay();
            long countOnOrBefore = Arrays.stream(finishEpochDays).filter(d -> d <= tEpoch).count();
            pByTarget = (double) countOnOrBefore / runs;
        }

        // mean capacity up to p85 week
        int p85Week = (int) ((p85.toEpochDay() - simStart.toEpochDay()) / 7) + 1;
        double capFactor = 0;
        int capCount = Math.min(p85Week, maxWeeks);
        for (int i = 0; i < capCount; i++) capFactor += capacity[i];
        capFactor = capCount > 0 ? capFactor / capCount : 1.0;

        String project = getProject(releaseIssues, release);
        return new Forecast(
            project, release, asOf, (int) remaining,
            p50, p85, p95, targetDate, pByTarget,
            weeklyThroughput.size(), windowStart, windowEnd,
            capFactor, runs, seed
        );
    }

    static List<Double> computeWeeklyThroughput(List<Issue> issues, String release, LocalDate from, LocalDate to) {
        // Build week buckets
        Map<Long, Double> buckets = new TreeMap<>();
        for (Issue issue : issues) {
            if (!release.equals(issue.fixVersion())) continue;
            if (issue.resolved() == null) continue;
            if (issue.resolved().isBefore(from) || issue.resolved().isAfter(to)) continue;
            LocalDate monday = issue.resolved().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            buckets.merge(monday.toEpochDay(), 1.0, Double::sum);
        }
        if (buckets.isEmpty()) return List.of();
        return new ArrayList<>(buckets.values());
    }

    private static LocalDate nextMonday(LocalDate d) {
        if (d.getDayOfWeek() == DayOfWeek.MONDAY) return d;
        return d.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private static LocalDate addWorkdays(LocalDate start, int days) {
        LocalDate d = start;
        int added = 0;
        while (added < days) {
            d = d.plusDays(1);
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) added++;
        }
        return d;
    }

    private static long percentileLong(long[] sorted, double pct) {
        int idx = (int) Math.ceil(pct * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }

    private static String getProject(List<Issue> issues, String release) {
        return issues.stream().map(Issue::project).filter(Objects::nonNull).findFirst().orElse("UNKNOWN");
    }
}
