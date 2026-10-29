package com.aida.engine;

import com.aida.contract.Issue;
import com.aida.contract.StatusChange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure utility: rebuild issue states as they existed at end of day d.
 */
public class Snapshots {

    private Snapshots() {}

    public static List<Issue> snapshot(List<Issue> issues, LocalDate d) {
        List<Issue> result = new ArrayList<>();
        for (Issue i : issues) {
            if (i.created() == null || i.created().isAfter(d)) continue;
            // rebuild status from history
            String status = "To Do";
            for (StatusChange sc : i.statusHistory()) {
                if (!sc.at().atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(d)) {
                    status = sc.status();
                }
            }
            // resolved: only if resolved on or before d
            LocalDate resolved = (i.resolved() != null && !i.resolved().isAfter(d)) ? i.resolved() : null;
            result.add(new Issue(
                i.key(), i.project(), i.type(), i.summary(), status,
                i.statusHistory(), i.created(), resolved,
                i.estimate(), i.epic(), i.epicName(), i.fixVersion(), i.assignee()
            ));
        }
        return result;
    }
}
