package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.Person;

/** Pure: no Spring, no I/O. Signature FROZEN; implemented by Person B in M3 (B2). */
public final class Capacity {

    private Capacity() {
    }

    /** Available share of the team per week from {@code start}; 1.0 = full team. */
    public static double[] byWeek(List<Person> team, List<Absence> absences, LocalDate start, int weeks) {
        throw new UnsupportedOperationException("Capacity.byWeek: not implemented yet (M3, Person B)");
    }
}
