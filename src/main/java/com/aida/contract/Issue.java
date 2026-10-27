package com.aida.contract;

import java.time.LocalDate;
import java.util.List;

/**
 * A Jira issue in AIDA's own shape.
 *
 * @param type          Story, Bug or Task
 * @param statusHistory oldest first; the first entry is "To Do" at creation
 * @param resolved      null while unresolved
 * @param estimate      story points; null when missing
 */
public record Issue(
        String key,
        String project,
        String type,
        String summary,
        String status,
        List<StatusChange> statusHistory,
        LocalDate created,
        LocalDate resolved,
        Double estimate,
        String epic,
        String epicName,
        String fixVersion,
        String assignee) {
}
