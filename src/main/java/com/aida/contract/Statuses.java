package com.aida.contract;

import java.util.List;
import java.util.Set;

/** Workflow statuses shared by the mock, adapters and engine. */
public final class Statuses {

    public static final String TO_DO = "To Do";
    public static final String IN_PROGRESS = "In Progress";
    public static final String IN_REVIEW = "In Review";
    public static final String QA = "QA";
    public static final String WAITING_FOR_CLIENT_UAT = "Waiting for Client UAT";
    public static final String DONE = "Done";

    /** All statuses in workflow order. */
    public static final List<String> WORKFLOW =
            List.of(TO_DO, IN_PROGRESS, IN_REVIEW, QA, WAITING_FOR_CLIENT_UAT, DONE);

    public static final Set<String> ACTIVE =
            Set.of(IN_PROGRESS, IN_REVIEW, QA, WAITING_FOR_CLIENT_UAT);

    public static final Set<String> CLIENT_SIDE = Set.of(WAITING_FOR_CLIENT_UAT);

    private Statuses() {
    }

    public static boolean isActive(String status) {
        return ACTIVE.contains(status);
    }

    public static boolean isClientSide(String status) {
        return CLIENT_SIDE.contains(status);
    }
}
