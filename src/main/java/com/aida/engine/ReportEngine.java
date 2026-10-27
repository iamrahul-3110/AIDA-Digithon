package com.aida.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;

/** Status report data per audience. Pure and stateless. Signature FROZEN; implemented by Person B in M6 (B5). */
public class ReportEngine {

    /**
     * @param audience team, management, client or hq
     */
    public Map<String, Object> build(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, String audience) {
        throw new UnsupportedOperationException("ReportEngine.build: not implemented yet (M6, Person B)");
    }
}
