package com.aida.engine;

import com.aida.contract.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What-if scenario engine. Never mutates inputs.
 */
public class WhatIfEngine {

    private WhatIfEngine() {}

    public static Forecast run(
            List<Issue> issues,
            String release,
            LocalDate asOf,
            List<Person> team,
            List<Absence> absences,
            WhatIf scenario) {

        // Deep-copy / filter issues
        List<Issue> modIssues = issues.stream()
            .filter(i -> {
                if (scenario.dropEpics() != null && !scenario.dropEpics().isEmpty()) {
                    if (scenario.dropEpics().contains(i.epic()) || scenario.dropEpics().contains(i.epicName())) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());

        // Modified team
        List<Person> modTeam = new ArrayList<>(team);
        if (scenario.removePeople() != null) {
            modTeam.removeIf(p -> scenario.removePeople().contains(p.name()));
        }
        if (scenario.addPeople() != null) {
            modTeam.addAll(scenario.addPeople());
        }

        LocalDate target = scenario.targetDate();

        return ForecastEngine.forecast(modIssues, release, asOf, modTeam, absences, target, 10000, 42);
    }
}
