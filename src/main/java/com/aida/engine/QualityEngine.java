package com.aida.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.aida.contract.DataQuality;
import com.aida.contract.Issue;
import com.aida.contract.QualityIssue;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;

/** Data quality score. Pure and stateless. Signature FROZEN; implemented by Person B in M4 (B3). */
public class QualityEngine {

    static final int STALE_DAYS = 14;
    static final int BULK_CLOSE_SIZE = 10;
    static final int MIN_HISTORY_WEEKS = 6;
    static final int MIN_SCORE = 60;

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(EngineDates.ZONE);

    /**
     * Scores the project's data at asOf: stale_status (active, no status change for 14+ days), missing_estimate
     * (Story without estimate), bulk_close (10+ issues resolved in the same minute). Score = 100 - 50 x stale share
     * of active items - 30 x missing share of stories - 15 if any bulk close, clamped and rounded. A forecast is
     * allowed with a score of 60+ and 6+ weeks of history. Only rules that found something are listed.
     */
    public DataQuality assess(List<Issue> issues, LocalDate asOf) {
        List<Issue> snapshot = Snapshots.snapshot(issues, asOf);

        List<Issue> active = snapshot.stream().filter(i -> Statuses.isActive(i.status())).toList();
        List<String> stale = active.stream()
                .filter(i -> ChronoUnit.DAYS.between(lastChange(i), asOf) >= STALE_DAYS)
                .map(Issue::key).sorted().toList();

        List<Issue> stories = snapshot.stream().filter(i -> "Story".equals(i.type())).toList();
        List<String> missing = stories.stream().filter(i -> i.estimate() == null).map(Issue::key).sorted().toList();

        Map<Instant, List<String>> bulk = bulkCloses(snapshot);
        List<String> bulkKeys = bulk.values().stream().flatMap(List::stream).sorted().toList();

        double staleShare = active.isEmpty() ? 0 : (double) stale.size() / active.size();
        double missingShare = stories.isEmpty() ? 0 : (double) missing.size() / stories.size();
        double raw = 100 - 50 * staleShare - 30 * missingShare - (bulk.isEmpty() ? 0 : 15);
        int score = (int) Math.round(Math.max(0, Math.min(100, raw)));

        List<QualityIssue> found = new ArrayList<>();
        if (!stale.isEmpty()) {
            found.add(new QualityIssue("stale_status", stale.size(), stale,
                    String.format("%d of %d active items (%d%%) have not changed status in %d+ days.", stale.size(),
                            active.size(), percent(staleShare), STALE_DAYS)));
        }
        if (!missing.isEmpty()) {
            found.add(new QualityIssue("missing_estimate", missing.size(), missing,
                    String.format("%d of %d stories (%d%%) have no estimate.", missing.size(), stories.size(),
                            percent(missingShare))));
        }
        if (!bulk.isEmpty()) {
            Map.Entry<Instant, List<String>> largest = bulk.entrySet().stream()
                    .max(Comparator.comparingInt(e -> e.getValue().size())).orElseThrow();
            found.add(new QualityIssue("bulk_close", bulkKeys.size(), bulkKeys,
                    String.format("%d items were resolved in %d bulk close(s) of %d+ items in the same minute "
                            + "(largest: %d at %s UTC).", bulkKeys.size(), bulk.size(), BULK_CLOSE_SIZE,
                            largest.getValue().size(), MINUTE.format(largest.getKey()))));
        }

        boolean allowed = score >= MIN_SCORE && historyWeeks(snapshot, asOf) >= MIN_HISTORY_WEEKS;
        String project = snapshot.isEmpty() ? null : snapshot.get(0).project();
        return new DataQuality(project, score, List.copyOf(found), allowed);
    }

    /** Day of the last status change (creation counts as the first). */
    static LocalDate lastChange(Issue issue) {
        List<StatusChange> history = issue.statusHistory();
        return history == null || history.isEmpty() ? issue.created()
                : EngineDates.day(history.get(history.size() - 1).at());
    }

    /** Groups of 10+ Done issues whose move to Done fell in the same minute, keyed by that minute. */
    private static Map<Instant, List<String>> bulkCloses(List<Issue> snapshot) {
        Map<Instant, List<String>> byMinute = new TreeMap<>();
        for (Issue issue : snapshot) {
            if (Statuses.DONE.equals(issue.status()) && issue.statusHistory() != null
                    && !issue.statusHistory().isEmpty()) {
                Instant minute = issue.statusHistory().get(issue.statusHistory().size() - 1).at()
                        .truncatedTo(ChronoUnit.MINUTES);
                byMinute.computeIfAbsent(minute, m -> new ArrayList<>()).add(issue.key());
            }
        }
        byMinute.values().removeIf(keys -> keys.size() < BULK_CLOSE_SIZE);
        return byMinute;
    }

    private static long historyWeeks(List<Issue> snapshot, LocalDate asOf) {
        return snapshot.stream().map(Issue::created).min(LocalDate::compareTo)
                .map(first -> ChronoUnit.DAYS.between(first, asOf) / 7).orElse(0L);
    }

    private static long percent(double share) {
        return Math.round(share * 100);
    }
}
