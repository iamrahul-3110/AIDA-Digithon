package com.aida.engine;

import java.time.LocalDate;
import java.util.List;

import com.aida.contract.DataQuality;
import com.aida.contract.Issue;

/** Data quality score. Pure and stateless. Signature FROZEN; implemented by Person B in M4 (B3). */
public class QualityEngine {

    public DataQuality assess(List<Issue> issues, LocalDate asOf) {
        throw new UnsupportedOperationException("QualityEngine.assess: not implemented yet (M4, Person B)");
    }
}
