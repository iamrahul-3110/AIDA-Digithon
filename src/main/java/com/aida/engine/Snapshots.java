package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Issue;

/** Pure: no Spring, no I/O. Signature FROZEN; implemented by Person B in M2 (B1). */
public final class Snapshots {

    private Snapshots() {
    }

    /** Issues as they stood at the end of day {@code d}: created on or before d, status and resolved rebuilt from history. */
    public static List<Issue> snapshot(List<Issue> issues, LocalDate d) {
        throw new UnsupportedOperationException("Snapshots.snapshot: not implemented yet (M2, Person B)");
    }
}
