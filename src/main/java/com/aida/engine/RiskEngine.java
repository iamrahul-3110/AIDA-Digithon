package com.aida.engine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aida.contract.Issue;
import com.aida.contract.Risk;
import com.aida.contract.RiskReport;
import com.aida.contract.Statuses;

/** Aging, client-wait and stale items. Pure and stateless. Signature FROZEN; implemented by Person B in M5 (B4). */
public class RiskEngine {

    static final int CLIENT_WAIT_DAYS = 5;
    static final int STALE_DAYS = 14;

    /**
     * Active items at asOf with their days in the current status. Each item gets at most one reason, checked in
     * this order: client_wait (Waiting for Client UAT for 5+ days), stale (14+ days), aging (more than twice the
     * median days of the active items in the same status). Side is client for client-side statuses, else team.
     * The bottleneck is the active status holding the most item-days. Items are sorted by days, longest first.
     */
    public RiskReport assess(List<Issue> issues, LocalDate asOf) {
        List<Issue> active = Snapshots.snapshot(issues, asOf).stream().filter(i -> Statuses.isActive(i.status()))
                .toList();
        Map<String, Double> medians = medianDaysByStatus(active, asOf);

        List<Risk> risks = new ArrayList<>();
        Map<String, Long> itemDays = new HashMap<>();
        for (Issue issue : active) {
            int days = daysInStatus(issue, asOf);
            itemDays.merge(issue.status(), (long) days, Long::sum);
            String reason = reason(issue, days, medians.get(issue.status()));
            if (reason != null) {
                String side = Statuses.isClientSide(issue.status()) ? "client" : "team";
                risks.add(new Risk(issue.key(), issue.summary(), reason, side, days, issue.assignee()));
            }
        }
        risks.sort(Comparator.comparingInt(Risk::days).reversed().thenComparing(Risk::key));

        String bottleneck = Statuses.WORKFLOW.stream().filter(itemDays::containsKey)
                .max(Comparator.comparingLong(itemDays::get)).orElse(null);
        String project = active.isEmpty() ? projectOf(issues) : active.get(0).project();
        return new RiskReport(project, asOf, bottleneck, List.copyOf(risks));
    }

    private static String reason(Issue issue, int days, Double median) {
        if (Statuses.isClientSide(issue.status()) && days >= CLIENT_WAIT_DAYS) {
            return "client_wait";
        }
        if (days >= STALE_DAYS) {
            return "stale";
        }
        if (isAging(days, median)) {
            return "aging";
        }
        return null;
    }

    static boolean isAging(int days, Double median) {
        return median != null && median > 0 && days > 2 * median;
    }

    /** Whole days since the last status change (creation counts as the first). */
    static int daysInStatus(Issue issue, LocalDate asOf) {
        return (int) ChronoUnit.DAYS.between(QualityEngine.lastChange(issue), asOf);
    }

    /** Median days in status per status, over the given (active) items. */
    static Map<String, Double> medianDaysByStatus(List<Issue> items, LocalDate asOf) {
        Map<String, List<Integer>> byStatus = new HashMap<>();
        for (Issue issue : items) {
            byStatus.computeIfAbsent(issue.status(), s -> new ArrayList<>()).add(daysInStatus(issue, asOf));
        }
        Map<String, Double> medians = new HashMap<>();
        byStatus.forEach((status, days) -> {
            List<Integer> sorted = days.stream().sorted().toList();
            int n = sorted.size();
            medians.put(status, n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
        });
        return medians;
    }

    private static String projectOf(List<Issue> issues) {
        return issues.isEmpty() ? null : issues.get(0).project();
    }
}
