package com.aida.adapter;

import com.aida.contract.Absence;
import com.aida.contract.Person;

import java.time.LocalDate;
import java.util.List;

public interface LeaveSource {
    List<Person> team(String project);
    List<Absence> absences(LocalDate from, LocalDate to);
}
