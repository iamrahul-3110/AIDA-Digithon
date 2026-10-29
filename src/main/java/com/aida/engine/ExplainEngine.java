package com.aida.engine;

import com.aida.contract.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Explains why the delivery date shifted between two asOf dates.
 */
public class ExplainEngine {

    private ExplainEngine() {}

    private static final List<String> ACTIVE_STATUSES = List.of("In Progress", "In Review", "QA", "Waiting for Client UAT");

    public static ChangeExplanation explain(
            List<Issue> issues,
            String release,
            LocalDate fromDate,
            LocalDate toDate,
            List<Person> team,
            List<Absence> absences) {

        Forecast forecastFrom = ForecastEngine.forecast(issues, release, fromDate, team, absences, null, 10000, 42);
        Forecast forecastTo   = ForecastEngine.forecast(issues, release, toDate,   team, absences, null, 10000, 42);

        LocalDate p85From = forecastFrom.p85();
        LocalDate p85To   = forecastTo.p85();
        int shiftDays = (p85From != null && p85To != null) ? (int) ChronoUnit.DAYS.between(p85From, p85To) : 0;

        List<Issue> snapFrom = Snapshots.snapshot(issues, fromDate);
        List<Issue> snapTo   = Snapshots.snapshot(issues, toDate);

        List<Issue> releaseFrom = snapFrom.stream().filter(i -> release.equals(i.fixVersion())).collect(Collectors.toList());
        List<Issue> releaseTo   = snapTo.stream()  .filter(i -> release.equals(i.fixVersion())).collect(Collectors.toList());

        List<Driver> drivers = new ArrayList<>();

        // scope_added: new issues added to release between dates
        Set<String> keysFrom = releaseFrom.stream().map(Issue::key).collect(Collectors.toSet());
        List<String> addedKeys = releaseTo.stream()
            .filter(i -> !keysFrom.contains(i.key()) && !"Done".equals(i.status()))
            .map(Issue::key).collect(Collectors.toList());
        if (!addedKeys.isEmpty()) {
            drivers.add(new Driver("scope_added",
                addedKeys.size() * 1.2,
                String.format("%d item(s) were added to the release scope and are not yet done.", addedKeys.size()),
                addedKeys));
        }

        // client_wait: Waiting for Client UAT 5+ days at toDate
        List<String> clientWaitKeys = releaseTo.stream()
            .filter(i -> "Waiting for Client UAT".equals(i.status()))
            .filter(i -> {
                LocalDate enteredWait = lastEnteredStatus(i, "Waiting for Client UAT");
                return enteredWait != null && ChronoUnit.DAYS.between(enteredWait, toDate) >= 5;
            })
            .map(Issue::key).collect(Collectors.toList());
        if (!clientWaitKeys.isEmpty()) {
            drivers.add(new Driver("client_wait",
                clientWaitKeys.size() * 1.5,
                String.format("%d item(s) have been waiting for client UAT for 5 or more days.", clientWaitKeys.size()),
                clientWaitKeys));
        }

        // leave: person-days of leave/holiday added between fromDate and toDate
        double leaveDays = countLeaveDays(team, absences, fromDate, toDate);
        if (leaveDays > 0) {
            drivers.add(new Driver("leave", leaveDays * 0.8,
                String.format("%.1f person-day(s) of leave or holidays fall within the delivery window.", leaveDays),
                List.of()));
        }

        // throughput_drop: last 4 weeks vs 12-week average
        List<Double> full = ForecastEngine.computeWeeklyThroughput(issues, release, toDate.minusWeeks(12), toDate);
        List<Double> recent = ForecastEngine.computeWeeklyThroughput(issues, release, toDate.minusWeeks(4), toDate);
        if (!full.isEmpty() && !recent.isEmpty()) {
            double avg12 = full.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double avg4  = recent.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (avg4 < avg12 * 0.8) {
                drivers.add(new Driver("throughput_drop", (avg12 - avg4) * 2,
                    String.format("Throughput dropped from %.1f to %.1f items/week over the last 4 weeks.", avg12, avg4),
                    List.of()));
            }
        }

        // aging: issues in a status > 2x the median for that status
        List<String> agingKeys = releaseTo.stream()
            .filter(i -> ACTIVE_STATUSES.contains(i.status()))
            .filter(i -> isAging(i, releaseTo, toDate))
            .map(Issue::key).collect(Collectors.toList());
        if (!agingKeys.isEmpty()) {
            drivers.add(new Driver("aging",
                agingKeys.size() * 1.0,
                String.format("%d item(s) have been in their current status more than twice the median for that stage.", agingKeys.size()),
                agingKeys));
        }

        // scale drivers so impactDays sum to shiftDays
        if (!drivers.isEmpty() && shiftDays != 0) {
            double rawSum = drivers.stream().mapToDouble(Driver::impactDays).sum();
            if (rawSum != 0) {
                double scale = (double) shiftDays / rawSum;
                drivers = drivers.stream()
                    .map(d -> new Driver(d.type(), d.impactDays() * scale, d.detail(), d.keys()))
                    .sorted(Comparator.comparingDouble(d -> -Math.abs(d.impactDays())))
                    .collect(Collectors.toList());
            }
        }

        String project = releaseTo.stream().map(Issue::project).filter(Objects::nonNull).findFirst().orElse("UNKNOWN");
        return new ChangeExplanation(project, release, fromDate, toDate, p85From, p85To, shiftDays, drivers);
    }

    private static LocalDate lastEnteredStatus(Issue i, String status) {
        if (i.statusHistory() == null) return null;
        return i.statusHistory().stream()
            .filter(sc -> status.equals(sc.status()))
            .map(sc -> sc.at().atZone(ZoneOffset.UTC).toLocalDate())
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private static double countLeaveDays(List<Person> team, List<Absence> absences, LocalDate from, LocalDate to) {
        double count = 0;
        for (Absence a : absences) {
            LocalDate start = a.start().isBefore(from) ? from : a.start();
            LocalDate end   = a.end().isAfter(to) ? to : a.end();
            if (!start.isAfter(end)) {
                LocalDate d = start;
                while (!d.isAfter(end)) {
                    java.time.DayOfWeek dow = d.getDayOfWeek();
                    if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) count++;
                    d = d.plusDays(1);
                }
            }
        }
        return count;
    }

    private static boolean isAging(Issue issue, List<Issue> allIssues, LocalDate asOf) {
        String status = issue.status();
        LocalDate entered = lastEnteredStatus(issue, status);
        if (entered == null) return false;
        long days = ChronoUnit.DAYS.between(entered, asOf);

        // compute median days in this status for all issues
        List<Long> statusDays = allIssues.stream()
            .filter(i -> status.equals(i.status()))
            .map(i -> {
                LocalDate e = lastEnteredStatus(i, status);
                return e == null ? 0L : ChronoUnit.DAYS.between(e, asOf);
            })
            .sorted()
            .collect(Collectors.toList());
        if (statusDays.isEmpty()) return false;
        double median = statusDays.get(statusDays.size() / 2);
        return days > 2 * median && median > 0;
    }
}
