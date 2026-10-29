package com.aida.engine;

import com.aida.contract.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data quality assessment engine.
 */
public class QualityEngine {

    private QualityEngine() {}

    private static final List<String> ACTIVE_STATUSES = List.of("In Progress", "In Review", "QA", "Waiting for Client UAT");

    public static DataQuality assess(List<Issue> issues, LocalDate asOf) {
        List<Issue> snap = Snapshots.snapshot(issues, asOf);

        List<Issue> active = snap.stream()
            .filter(i -> ACTIVE_STATUSES.contains(i.status()))
            .collect(Collectors.toList());

        List<Issue> stories = snap.stream()
            .filter(i -> "Story".equals(i.type()))
            .collect(Collectors.toList());

        List<QualityIssue> qualityIssues = new ArrayList<>();
        int score = 100;

        // stale_status: active issues with no status change for 14+ days
        List<String> staleKeys = new ArrayList<>();
        for (Issue i : active) {
            LocalDate lastChange = lastStatusChange(i);
            if (lastChange != null && ChronoUnit.DAYS.between(lastChange, asOf) >= 14) {
                staleKeys.add(i.key());
            }
        }
        if (!staleKeys.isEmpty() && !active.isEmpty()) {
            double staleShare = (double) staleKeys.size() / active.size();
            int penalty = (int) Math.round(50 * staleShare);
            score -= penalty;
            qualityIssues.add(new QualityIssue(
                "stale_status", staleKeys.size(), staleKeys,
                String.format("%d active item(s) have had no status change for 14+ days (%.0f%% of active).",
                    staleKeys.size(), staleShare * 100)
            ));
        }

        // missing_estimate: stories without estimate
        List<String> noEstKeys = stories.stream()
            .filter(i -> i.estimate() == null)
            .map(Issue::key)
            .collect(Collectors.toList());
        if (!noEstKeys.isEmpty() && !stories.isEmpty()) {
            double missingShare = (double) noEstKeys.size() / stories.size();
            int penalty = (int) Math.round(30 * missingShare);
            score -= penalty;
            qualityIssues.add(new QualityIssue(
                "missing_estimate", noEstKeys.size(), noEstKeys,
                String.format("%d story(s) are missing story-point estimates (%.0f%% of stories).",
                    noEstKeys.size(), missingShare * 100)
            ));
        }

        // bulk_close: 10+ issues resolved in the same minute
        boolean hasBulkClose = detectBulkClose(issues);
        if (hasBulkClose) {
            score -= 15;
            qualityIssues.add(new QualityIssue(
                "bulk_close", 1, List.of(),
                "A bulk close event was detected: 10 or more issues were resolved within the same minute."
            ));
        }

        score = Math.max(0, Math.min(100, score));

        // forecastAllowed: score >= 60 AND at least 6 weeks of history
        LocalDate earliest = snap.stream()
            .map(Issue::created)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(asOf);
        long weeksOfHistory = ChronoUnit.WEEKS.between(earliest, asOf);
        boolean forecastAllowed = score >= 60 && weeksOfHistory >= 6;

        String project = snap.stream().map(Issue::project).filter(Objects::nonNull).findFirst().orElse("UNKNOWN");
        return new DataQuality(project, score, qualityIssues, forecastAllowed);
    }

    private static LocalDate lastStatusChange(Issue i) {
        if (i.statusHistory() == null || i.statusHistory().isEmpty()) return i.created();
        Instant latest = i.statusHistory().stream()
            .map(StatusChange::at)
            .max(Comparator.naturalOrder())
            .orElse(null);
        return latest == null ? i.created() : latest.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static boolean detectBulkClose(List<Issue> issues) {
        Map<String, Long> minuteBuckets = new HashMap<>();
        for (Issue i : issues) {
            if (i.resolved() == null) continue;
            // Use statusHistory to find exact resolution instant
            if (i.statusHistory() != null) {
                for (StatusChange sc : i.statusHistory()) {
                    if ("Done".equals(sc.status())) {
                        // bucket by minute
                        String bucket = sc.at().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString();
                        minuteBuckets.merge(bucket, 1L, Long::sum);
                    }
                }
            }
        }
        return minuteBuckets.values().stream().anyMatch(c -> c >= 10);
    }
}
