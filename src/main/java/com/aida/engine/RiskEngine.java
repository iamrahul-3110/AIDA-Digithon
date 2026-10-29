package com.aida.engine;

import com.aida.contract.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Risk assessment engine.
 */
public class RiskEngine {

    private RiskEngine() {}

    private static final List<String> ACTIVE_STATUSES = List.of("In Progress", "In Review", "QA", "Waiting for Client UAT");

    public static RiskReport assess(List<Issue> issues, LocalDate asOf) {
        List<Issue> snap = Snapshots.snapshot(issues, asOf);

        List<Risk> risks = new ArrayList<>();
        for (Issue i : snap) {
            if (!ACTIVE_STATUSES.contains(i.status())) continue;

            LocalDate enteredStatus = lastEnteredStatus(i, i.status());
            int days = enteredStatus != null ? (int) ChronoUnit.DAYS.between(enteredStatus, asOf) : 0;

            String reason = null;
            String side = "team";
            if ("Waiting for Client UAT".equals(i.status()) && days >= 3) {
                reason = "client_wait";
                side = "client";
            } else if (days >= 14) {
                reason = "stale";
            } else if (isAgingRisk(i, snap, asOf)) {
                reason = "aging";
            }

            if (reason != null) {
                risks.add(new Risk(i.key(), i.summary(), reason, side, days, i.assignee()));
            }
        }

        risks.sort(Comparator.comparingInt(Risk::days).reversed());

        // bottleneckStage: active status with most item-days
        Map<String, Long> stageDays = new HashMap<>();
        for (Issue i : snap) {
            if (!ACTIVE_STATUSES.contains(i.status())) continue;
            LocalDate entered = lastEnteredStatus(i, i.status());
            long d = entered != null ? ChronoUnit.DAYS.between(entered, asOf) : 0;
            stageDays.merge(i.status(), d, Long::sum);
        }
        String bottleneck = stageDays.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        String project = snap.stream().map(Issue::project).filter(Objects::nonNull).findFirst().orElse("UNKNOWN");
        return new RiskReport(project, asOf, bottleneck, risks);
    }

    private static LocalDate lastEnteredStatus(Issue i, String status) {
        if (i.statusHistory() == null) return i.created();
        return i.statusHistory().stream()
            .filter(sc -> status.equals(sc.status()))
            .map(sc -> sc.at().atZone(ZoneOffset.UTC).toLocalDate())
            .max(Comparator.naturalOrder())
            .orElse(i.created());
    }

    private static boolean isAgingRisk(Issue issue, List<Issue> allIssues, LocalDate asOf) {
        String status = issue.status();
        LocalDate entered = lastEnteredStatus(issue, status);
        if (entered == null) return false;
        long days = ChronoUnit.DAYS.between(entered, asOf);
        List<Long> statusDays = allIssues.stream()
            .filter(i -> status.equals(i.status()))
            .map(i -> {
                LocalDate e = lastEnteredStatus(i, status);
                return e == null ? 0L : ChronoUnit.DAYS.between(e, asOf);
            })
            .sorted().collect(Collectors.toList());
        if (statusDays.isEmpty()) return false;
        double median = statusDays.get(statusDays.size() / 2);
        return days > 2 * median && median > 0;
    }
}
