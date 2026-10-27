package com.aida.adapter;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.Person;

/** Reads the team and its leaves and public holidays (the mock calendar in the MVP). FROZEN. */
public interface LeaveSource {

    List<Person> team(String project);

    /** Leaves and holidays overlapping {@code from..to}, both inclusive. */
    List<Absence> absences(LocalDate from, LocalDate to);
}
