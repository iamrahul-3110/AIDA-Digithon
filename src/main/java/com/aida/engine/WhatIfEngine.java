package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.WhatIf;

/** Forecast under a changed scope or team; never mutates inputs. Signature FROZEN; implemented by Person B in M5 (B4). */
public class WhatIfEngine {

    public Forecast run(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, WhatIf whatIf) {
        throw new UnsupportedOperationException("WhatIfEngine.run: not implemented yet (M5, Person B)");
    }
}
