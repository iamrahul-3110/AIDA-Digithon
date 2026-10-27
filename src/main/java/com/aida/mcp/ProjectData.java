package com.aida.mcp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Release;

/**
 * Loads everything a tool needs for one project through JiraSource and LeaveSource, with the data window
 * the engine needs. Tools use this instead of calling the adapters. FROZEN signature.
 *
 * <p>M0 stub; M2 (Person A) implements it on top of the HTTP adapters.
 */
@Component
public class ProjectData {

    /** One project's data: all its issues, its team, absences around asOf, and its releases. */
    public record Data(List<Issue> issues, List<Person> team, List<Absence> absences, List<Release> releases) {
    }

    public Data load(String project, LocalDate asOf) {
        throw new UnsupportedOperationException("ProjectData.load: not implemented yet (M2, Person A)");
    }
}
