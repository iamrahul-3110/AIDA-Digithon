package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.ChangeExplanation;
import com.aida.contract.Issue;
import com.aida.contract.Person;

/** Why the p85 date moved. Pure and stateless. Signature FROZEN; implemented by Person B in M5 (B4). */
public class ExplainEngine {

    public ChangeExplanation explain(List<Issue> issues, String release, LocalDate fromDate, LocalDate toDate,
            List<Person> team, List<Absence> absences) {
        throw new UnsupportedOperationException("ExplainEngine.explain: not implemented yet (M5, Person B)");
    }
}
