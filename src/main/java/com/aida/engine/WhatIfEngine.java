package com.aida.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.aida.contract.Absence;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Statuses;
import com.aida.contract.WhatIf;

/** Forecast under a changed scope or team; never mutates inputs. Signature FROZEN; implemented by Person B in M5 (B4). */
public class WhatIfEngine {

    private final ForecastEngine forecastEngine = new ForecastEngine();

    /**
     * Drops the not-Done release items of each epic in {@code dropEpics} (matched by epic key or epic name, ignoring
     * case), removes and adds people, and forecasts with 10,000 runs and seed 42. A changed team scales every
     * throughput sample by new team size / old team size and recomputes capacity with the new team (added people
     * get their country's holidays).
     */
    public Forecast run(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, WhatIf whatIf) {
        Set<String> drop = lower(whatIf.dropEpics());
        List<Issue> scope = Snapshots.snapshot(issues, asOf).stream()
                .filter(i -> !(release.equals(i.fixVersion()) && !Statuses.DONE.equals(i.status())
                        && (matches(drop, i.epic()) || matches(drop, i.epicName()))))
                .toList();

        Set<String> removed = lower(whatIf.removePeople());
        List<Person> newTeam = new ArrayList<>(team.stream().filter(p -> !matches(removed, p.name())).toList());
        if (whatIf.addPeople() != null) {
            for (Person added : whatIf.addPeople()) {
                if (newTeam.stream().noneMatch(p -> p.name().equalsIgnoreCase(added.name()))) {
                    newTeam.add(added);
                }
            }
        }
        double scale = team.isEmpty() ? 1.0 : (double) newTeam.size() / team.size();

        return forecastEngine.forecast(scope, release, asOf, List.copyOf(newTeam), absences, whatIf.targetDate(),
                ForecastEngine.DEFAULT_RUNS, ForecastEngine.DEFAULT_SEED, scale);
    }

    private static boolean matches(Set<String> lowered, String value) {
        return value != null && lowered.contains(lower(value));
    }

    private static Set<String> lower(List<String> values) {
        return values == null ? Set.of()
                : values.stream().filter(v -> v != null).map(WhatIfEngine::lower).collect(Collectors.toSet());
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
