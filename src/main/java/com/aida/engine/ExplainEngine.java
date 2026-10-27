package com.aida.engine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.aida.contract.Absence;
import com.aida.contract.ChangeExplanation;
import com.aida.contract.Driver;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Statuses;

/** Why the p85 date moved. Pure and stateless. Signature FROZEN; implemented by Person B in M5 (B4). */
public class ExplainEngine {

    private final ForecastEngine forecastEngine = new ForecastEngine();

    /**
     * Forecasts at fromDate and toDate (each on its own snapshot, 10,000 runs, seed 42); shiftDays = p85To - p85From.
     * Candidate drivers, each with a raw weight in days carrying the sign of its effect:
     * <ul>
     * <li>scope_added: release items created after fromDate, up to toDate (each worth one item of throughput);
     * <li>client_wait: release items in Waiting for Client UAT for 5+ days at toDate (one item each);
     * <li>leave: leave and holiday person-days in the toDate run's window (toDate..p85To) minus those in the fromDate
     * run's window, in team-days; listed whenever the toDate window holds any absence;
     * <li>aging: active release items over twice the median days for their status at toDate (half an item each);
     * <li>throughput_drop: when the last 4 weeks are below the 12-week average at toDate, the change in days the
     * remaining items need at the two runs' average throughput.
     * </ul>
     * Weights are scaled so impactDays (one decimal) sum exactly to shiftDays, the rounding remainder going to the
     * largest driver; with shiftDays 0 every impact is 0. If no candidate carries weight while the date moved, a
     * throughput_drop driver states that the move comes from sampled throughput.
     */
    public ChangeExplanation explain(List<Issue> issues, String release, LocalDate fromDate, LocalDate toDate,
            List<Person> team, List<Absence> absences) {
        Forecast from = forecastEngine.forecast(issues, release, fromDate, team, absences, null);
        Forecast to = forecastEngine.forecast(issues, release, toDate, team, absences, null);
        int shift = (int) ChronoUnit.DAYS.between(from.p85(), to.p85());

        List<Issue> snapshot = Snapshots.snapshot(issues, toDate);
        List<Issue> scope = snapshot.stream().filter(i -> release.equals(i.fixVersion())).toList();
        double[] samplesTo = ForecastEngine.throughputSamples(snapshot, toDate);
        double[] samplesFrom = ForecastEngine.throughputSamples(Snapshots.snapshot(issues, fromDate), fromDate);
        double avgTo = mean(samplesTo);
        double perItem = 7 / Math.max(avgTo, 1.0);

        List<Candidate> candidates = new ArrayList<>();
        scopeAdded(scope, fromDate, toDate, release, perItem, candidates);
        clientWait(scope, toDate, perItem, candidates);
        leave(team, absences, from, to, candidates);
        aging(scope, toDate, perItem, candidates);
        throughputDrop(samplesTo, avgTo, mean(samplesFrom), to.remainingItems(), candidates);

        return new ChangeExplanation(to.project(), release, fromDate, toDate, from.p85(), to.p85(), shift,
                scale(candidates, shift, avgTo, mean(samplesFrom)));
    }

    private record Candidate(String type, double weight, String detail, List<String> keys) {
    }

    private static void scopeAdded(List<Issue> scope, LocalDate fromDate, LocalDate toDate, String release,
            double perItem, List<Candidate> out) {
        List<String> added = scope.stream().filter(i -> i.created().isAfter(fromDate)).map(Issue::key).sorted()
                .toList();
        if (!added.isEmpty()) {
            long before = scope.size() - added.size();
            String growth = before == 0 ? "" : String.format(" (+%d%%)", Math.round(100.0 * added.size() / before));
            out.add(new Candidate("scope_added", added.size() * perItem,
                    String.format("%d items were added to release %s between %s and %s%s.", added.size(), release,
                            fromDate, toDate, growth),
                    added));
        }
    }

    private static void clientWait(List<Issue> scope, LocalDate toDate, double perItem, List<Candidate> out) {
        List<Issue> waiting = scope.stream().filter(i -> Statuses.isClientSide(i.status()))
                .filter(i -> RiskEngine.daysInStatus(i, toDate) >= RiskEngine.CLIENT_WAIT_DAYS)
                .sorted(Comparator.comparing(Issue::key)).toList();
        if (!waiting.isEmpty()) {
            int longest = waiting.stream().mapToInt(i -> RiskEngine.daysInStatus(i, toDate)).max().orElse(0);
            out.add(new Candidate("client_wait", waiting.size() * perItem,
                    String.format("%d items have been waiting for client UAT for %d+ days (longest %d days).",
                            waiting.size(), RiskEngine.CLIENT_WAIT_DAYS, longest),
                    waiting.stream().map(Issue::key).toList()));
        }
    }

    private static void leave(List<Person> team, List<Absence> absences, Forecast from, Forecast to,
            List<Candidate> out) {
        if (team == null || team.isEmpty() || absences == null) {
            return;
        }
        int before = Capacity.absentPersonDays(team, absences, from.asOf(), from.p85());
        int now = Capacity.absentPersonDays(team, absences, to.asOf(), to.p85());
        if (now == 0) {
            return;
        }
        // Who is out in the current window, by person-days, for the sentence and the keys.
        Map<String, Integer> byPerson = new TreeMap<>();
        List<String> keys = new ArrayList<>();
        for (Absence a : absences) {
            if (a.end().isBefore(to.asOf()) || a.start().isAfter(to.p85())) {
                continue;
            }
            List<Person> hit = team.stream().filter(p -> Capacity.appliesTo(a, p)).toList();
            if (hit.isEmpty()) {
                continue;
            }
            keys.add(a.id());
            for (Person p : hit) {
                int days = Capacity.absentPersonDays(List.of(p), List.of(a), to.asOf(), to.p85());
                if (days > 0) {
                    byPerson.merge(p.name(), days, Integer::sum);
                }
            }
        }
        StringBuilder who = new StringBuilder();
        byPerson.forEach((name, days) -> who.append(who.isEmpty() ? "" : ", ").append(name).append(' ').append(days));
        double weight = (now - before) / (double) team.size() * 7 / 5;
        out.add(new Candidate("leave", weight,
                String.format("%d leave and holiday person-days fall in the forecast window %s to %s (%s); "
                        + "the earlier forecast's window had %d.", now, to.asOf(), to.p85(), who, before),
                keys.stream().sorted().toList()));
    }

    private static void aging(List<Issue> scope, LocalDate toDate, double perItem, List<Candidate> out) {
        List<Issue> active = scope.stream().filter(i -> Statuses.isActive(i.status())).toList();
        Map<String, Double> medians = RiskEngine.medianDaysByStatus(active, toDate);
        List<String> aging = active.stream()
                .filter(i -> RiskEngine.isAging(RiskEngine.daysInStatus(i, toDate), medians.get(i.status())))
                .map(Issue::key).sorted().toList();
        if (!aging.isEmpty()) {
            out.add(new Candidate("aging", aging.size() * perItem / 2,
                    String.format("%d of %d active items have been in their status for more than twice the median "
                            + "time for that status.", aging.size(), active.size()),
                    aging));
        }
    }

    private static void throughputDrop(double[] samplesTo, double avgTo, double avgFrom, int remaining,
            List<Candidate> out) {
        if (samplesTo.length < 4) {
            return;
        }
        double last4 = mean(Arrays.copyOfRange(samplesTo, samplesTo.length - 4, samplesTo.length));
        if (last4 >= avgTo) {
            return;
        }
        double weight = remaining * 7 * (1 / Math.max(avgTo, 0.1) - 1 / Math.max(avgFrom, 0.1));
        out.add(new Candidate("throughput_drop", weight,
                String.format("Throughput over the last 4 weeks averaged %.1f items per week, below the 12-week "
                        + "average of %.1f.", last4, avgTo),
                List.of()));
    }

    /** Scales weights so the impacts, in tenths of a day, sum exactly to shift days. */
    private static List<Driver> scale(List<Candidate> candidates, int shift, double avgTo, double avgFrom) {
        double total = candidates.stream().mapToDouble(Candidate::weight).sum();
        if (shift != 0 && Math.abs(total) < 1e-9) {
            candidates = new ArrayList<>(candidates.stream().filter(c -> !"throughput_drop".equals(c.type())).toList());
            candidates.add(new Candidate("throughput_drop", 1.0, String.format(
                    "No single driver stands out; the %d-day move comes from sampled throughput (%.1f items per week "
                            + "now, %.1f before).", shift, avgTo, avgFrom),
                    List.of()));
            total = candidates.stream().mapToDouble(Candidate::weight).sum();
        }
        long[] tenths = new long[candidates.size()];
        if (shift != 0) {
            long sum = 0;
            int largest = 0;
            for (int i = 0; i < tenths.length; i++) {
                tenths[i] = Math.round(candidates.get(i).weight() * shift * 10 / total);
                sum += tenths[i];
                if (Math.abs(tenths[i]) > Math.abs(tenths[largest])) {
                    largest = i;
                }
            }
            tenths[largest] += shift * 10L - sum;
        }
        List<Driver> drivers = new ArrayList<>();
        for (int i = 0; i < tenths.length; i++) {
            Candidate c = candidates.get(i);
            drivers.add(new Driver(c.type(), tenths[i] / 10.0, c.detail(), c.keys()));
        }
        drivers.sort(Comparator.comparingDouble((Driver d) -> Math.abs(d.impactDays())).reversed()
                .thenComparing(Driver::type));
        return List.copyOf(drivers);
    }

    private static double mean(double[] values) {
        return Arrays.stream(values).average().orElse(0);
    }
}
