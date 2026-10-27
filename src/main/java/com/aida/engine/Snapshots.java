package com.aida.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.aida.contract.Issue;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;

/** Pure: no Spring, no I/O. Signature FROZEN; implemented by Person B in M2 (B1). */
public final class Snapshots {

    private Snapshots() {
    }

    /**
     * Issues as they stood at the end of day {@code d}: created on or before d, status and resolved rebuilt from history.
     *
     * <p>The history is cut at the end of d, so later engines cannot see the future. The status is the last entry
     * left; resolved is the date of the last move to Done when that status is Done, else null (a reopened issue is
     * unresolved). Inputs are never mutated.
     */
    public static List<Issue> snapshot(List<Issue> issues, LocalDate d) {
        Instant end = EngineDates.endOfDay(d);
        List<Issue> out = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.created() == null || issue.created().isAfter(d)) {
                continue;
            }
            List<StatusChange> history = issue.statusHistory() == null ? List.of()
                    : issue.statusHistory().stream().filter(c -> c.at().isBefore(end)).toList();
            String status;
            LocalDate resolved;
            if (history.isEmpty()) {
                status = Statuses.TO_DO;
                resolved = null;
            } else {
                StatusChange last = history.get(history.size() - 1);
                status = last.status();
                resolved = Statuses.DONE.equals(status) ? EngineDates.day(last.at()) : null;
            }
            out.add(new Issue(issue.key(), issue.project(), issue.type(), issue.summary(), status, history,
                    issue.created(), resolved, issue.estimate(), issue.epic(), issue.epicName(), issue.fixVersion(),
                    issue.assignee()));
        }
        return List.copyOf(out);
    }
}
