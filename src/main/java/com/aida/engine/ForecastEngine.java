package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;

/** Monte Carlo delivery forecast. Pure and stateless. Signatures FROZEN; implemented by Person B in M3 (B2). */
public class ForecastEngine {

    public static final int DEFAULT_RUNS = 10_000;
    public static final long DEFAULT_SEED = 42L;

    /**
     * @param targetDate nullable; pByTarget is null without one
     */
    public Forecast forecast(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, LocalDate targetDate, int runs, long seed) {
        throw new UnsupportedOperationException("ForecastEngine.forecast: not implemented yet (M3, Person B)");
    }

    /** {@link #forecast(List, String, LocalDate, List, List, LocalDate, int, long)} with 10,000 runs and seed 42. */
    public Forecast forecast(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, LocalDate targetDate) {
        return forecast(issues, release, asOf, team, absences, targetDate, DEFAULT_RUNS, DEFAULT_SEED);
    }
}
