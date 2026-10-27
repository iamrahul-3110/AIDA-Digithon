package com.aida.testdata;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.aida.adapter.LeaveSource;
import com.aida.contract.Absence;
import com.aida.contract.Person;

/** In-memory LeaveSource over fixed teams (by project) and absences. */
public class FakeLeaveSource implements LeaveSource {

    private final Map<String, List<Person>> teams;
    private final List<Absence> absences;

    public FakeLeaveSource(Map<String, List<Person>> teams, List<Absence> absences) {
        this.teams = Map.copyOf(teams);
        this.absences = List.copyOf(absences);
    }

    @Override
    public List<Person> team(String project) {
        return teams.getOrDefault(project, List.of());
    }

    @Override
    public List<Absence> absences(LocalDate from, LocalDate to) {
        return absences.stream().filter(a -> !a.end().isBefore(from) && !a.start().isAfter(to)).toList();
    }
}
