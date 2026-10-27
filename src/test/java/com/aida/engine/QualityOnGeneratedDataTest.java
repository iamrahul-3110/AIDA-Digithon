package com.aida.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aida.contract.DataQuality;
import com.aida.contract.Issue;
import com.aida.contract.QualityIssue;
import com.aida.mock.DataGenerator;
import com.aida.mock.Dataset;

/** QualityEngine on the demo data (no HTTP): OPS must block a forecast, SHOP must allow one. */
class QualityOnGeneratedDataTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    static final Dataset DATA = DataGenerator.generate(AS_OF, 7);
    final QualityEngine engine = new QualityEngine();

    static List<Issue> project(String key) {
        return DATA.issues().stream().filter(i -> key.equals(i.project())).toList();
    }

    @Test
    void opsScoresBelowSixtyWithStaleItemsListed() {
        DataQuality q = engine.assess(project("OPS"), AS_OF);

        assertThat(q.score()).isLessThan(60);
        assertThat(q.forecastAllowed()).isFalse();
        assertThat(q.issues()).extracting(QualityIssue::type).contains("stale_status");
        assertThat(q.issues()).filteredOn(i -> i.type().equals("stale_status")).singleElement()
                .satisfies(i -> assertThat(i.keys()).isNotEmpty());
    }

    @Test
    void shopScoresSixtyOrAboveAndAllowsForecast() {
        DataQuality q = engine.assess(project("SHOP"), AS_OF);

        assertThat(q.score()).isGreaterThanOrEqualTo(60);
        assertThat(q.forecastAllowed()).isTrue();
    }
}
