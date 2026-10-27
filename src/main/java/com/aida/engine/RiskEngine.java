package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.Issue;
import com.aida.contract.RiskReport;

/** Aging, client-wait and stale items. Pure and stateless. Signature FROZEN; implemented by Person B in M5 (B4). */
public class RiskEngine {

    public RiskReport assess(List<Issue> issues, LocalDate asOf) {
        throw new UnsupportedOperationException("RiskEngine.assess: not implemented yet (M5, Person B)");
    }
}
