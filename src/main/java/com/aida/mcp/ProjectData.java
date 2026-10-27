package com.aida.mcp;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aida.adapter.JiraSource;
import com.aida.adapter.LeaveSource;
import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Release;

/**
 * Loads everything a tool needs for one project through JiraSource and LeaveSource, with the data window
 * the engine needs. Tools use this instead of calling the adapters. FROZEN signature.
 */
@Component
public class ProjectData {

    /** Weeks of history before asOf the forecast samples throughput from. */
    public static final int HISTORY_WEEKS = 12;
    /** How far after asOf leaves and holidays are loaded for capacity. */
    public static final int FORECAST_MONTHS = 6;

    /** One project's data: all its issues, its team, absences around asOf, and its releases. */
    public record Data(List<Issue> issues, List<Person> team, List<Absence> absences, List<Release> releases) {
    }

    private final JiraSource jira;
    private final LeaveSource leave;

    public ProjectData(JiraSource jira, LeaveSource leave) {
        this.jira = jira;
        this.leave = leave;
    }

    /**
     * All of the project's issues (the engine snapshots them at any date and filters by release), its team, its
     * releases, and the absences from asOf - 12 weeks to asOf + 6 months that touch the team: the members' leaves and
     * public holidays in the members' countries.
     */
    public Data load(String project, LocalDate asOf) {
        List<Issue> issues = jira.issues(project, null);
        List<Person> team = leave.team(project);
        Set<String> names = team.stream().map(Person::name).collect(Collectors.toSet());
        Set<String> countries = team.stream().map(Person::country).collect(Collectors.toSet());
        List<Absence> absences = leave.absences(asOf.minusWeeks(HISTORY_WEEKS), asOf.plusMonths(FORECAST_MONTHS))
                .stream()
                .filter(a -> a.person() == null ? countries.contains(a.country()) : names.contains(a.person()))
                .toList();
        return new Data(issues, team, absences, jira.releases(project));
    }
}
