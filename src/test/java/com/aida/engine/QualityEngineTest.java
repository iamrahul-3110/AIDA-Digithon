package com.aida.engine;

import static com.aida.testdata.TestData.issue;
import static com.aida.testdata.TestData.open;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aida.contract.DataQuality;
import com.aida.contract.Issue;
import com.aida.contract.QualityIssue;
import com.aida.contract.Statuses;
import com.aida.testdata.Fixtures;

class QualityEngineTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    final QualityEngine engine = new QualityEngine();

    /** An old, clean issue so the project has 6+ weeks of history. */
    static Issue oldDone(String key) {
        return issue(key).created(AS_OF.minusWeeks(20)).done(AS_OF.minusWeeks(18)).build();
    }

    @Test
    void cleanDataScoresHundredAndAllowsForecast() {
        List<Issue> issues = new ArrayList<>(open("SHOP", "3.2", Statuses.IN_PROGRESS, AS_OF.minusDays(3), 5));
        issues.add(oldDone("SHOP-1"));

        DataQuality q = engine.assess(issues, AS_OF);

        assertThat(q.project()).isEqualTo("SHOP");
        assertThat(q.score()).isEqualTo(100);
        assertThat(q.issues()).isEmpty();
        assertThat(q.forecastAllowed()).isTrue();
    }

    @Test
    void staleStatusCountsActiveItemsUnchangedFor14Days() {
        List<Issue> issues = new ArrayList<>();
        issues.addAll(open("SHOP", "3.2", Statuses.IN_PROGRESS, AS_OF.minusDays(14), 3)); // stale, exactly 14
        issues.addAll(open("SHOP", "3.2", Statuses.QA, AS_OF.minusDays(13), 7)); // fresh
        issues.addAll(open("SHOP", "3.2", Statuses.TO_DO, AS_OF.minusDays(60), 4)); // not active
        issues.add(oldDone("SHOP-1"));

        DataQuality q = engine.assess(issues, AS_OF);

        QualityIssue stale = only(q, "stale_status");
        assertThat(stale.count()).isEqualTo(3);
        assertThat(stale.detail()).isEqualTo("3 of 10 active items (30%) have not changed status in 14+ days.");
        assertThat(q.score()).isEqualTo(85); // 100 - 50 x 0.3
    }

    @Test
    void missingEstimateCountsOnlyStories() {
        List<Issue> issues = new ArrayList<>();
        issues.add(issue("SHOP-1").created(AS_OF.minusWeeks(10)).estimate(null).build());
        issues.add(issue("SHOP-2").created(AS_OF.minusWeeks(10)).build());
        issues.add(issue("SHOP-3").created(AS_OF.minusWeeks(10)).type("Bug").estimate(null).build());

        DataQuality q = engine.assess(issues, AS_OF);

        QualityIssue missing = only(q, "missing_estimate");
        assertThat(missing.keys()).containsExactly("SHOP-1");
        assertThat(missing.detail()).isEqualTo("1 of 2 stories (50%) have no estimate.");
        assertThat(q.score()).isEqualTo(85); // 100 - 30 x 0.5
    }

    @Test
    void bulkCloseNeedsTenInTheSameMinute() {
        Instant minute = Instant.parse("2026-10-01T14:03:00Z");
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            issues.add(issue("OPS-" + i).created(AS_OF.minusWeeks(10)).done(minute.plusSeconds(i * 5)).build());
        }
        for (int i = 10; i < 19; i++) { // 9 in another minute: not a bulk close
            issues.add(issue("OPS-" + i).created(AS_OF.minusWeeks(10)).done(minute.plusSeconds(3600)).build());
        }

        DataQuality q = engine.assess(issues, AS_OF);

        QualityIssue bulk = only(q, "bulk_close");
        assertThat(bulk.count()).isEqualTo(10);
        assertThat(bulk.detail()).contains("10 items").contains("2026-10-01 14:03 UTC");
        assertThat(q.score()).isEqualTo(85);
    }

    @Test
    void scoreCombinesAllRulesAndClampsAtZero() {
        Instant minute = Instant.parse("2026-10-01T14:03:00Z");
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            issues.add(issue("OPS-A" + i).created(AS_OF.minusWeeks(10)).estimate(null)
                    .moved(Statuses.IN_PROGRESS, AS_OF.minusDays(30)).build());
        }
        for (int i = 0; i < 12; i++) {
            issues.add(issue("OPS-B" + i).created(AS_OF.minusWeeks(10)).type("Task").done(minute).build());
        }

        DataQuality q = engine.assess(issues, AS_OF);

        // 100 - 50 x 1.0 - 30 x 1.0 - 15 = 5
        assertThat(q.score()).isEqualTo(5);
        assertThat(q.forecastAllowed()).isFalse();
        assertThat(q.issues()).extracting(QualityIssue::type)
                .containsExactly("stale_status", "missing_estimate", "bulk_close");
    }

    @Test
    void scoreRoundsHalfUp() {
        // 1 of 8 active stale: 100 - 6.25 = 93.75 -> 94
        List<Issue> issues = new ArrayList<>(open("SHOP", "3.2", Statuses.IN_PROGRESS, AS_OF.minusDays(20), 1));
        issues.addAll(open("SHOP", "3.2", Statuses.QA, AS_OF.minusDays(2), 7));
        issues.add(oldDone("SHOP-1"));

        assertThat(engine.assess(issues, AS_OF).score()).isEqualTo(94);
    }

    @Test
    void shortHistoryBlocksForecastEvenWithAGoodScore() {
        List<Issue> issues = open("SHOP", "3.2", Statuses.IN_PROGRESS, AS_OF.minusDays(3), 5);

        DataQuality q = engine.assess(issues, AS_OF);

        assertThat(q.score()).isEqualTo(100);
        assertThat(q.forecastAllowed()).isFalse();
    }

    @Test
    void scoreBelowSixtyBlocksForecast() {
        List<Issue> issues = new ArrayList<>(open("OPS", "Q4", Statuses.IN_PROGRESS, AS_OF.minusDays(30), 9));
        issues.addAll(open("OPS", "Q4", Statuses.IN_PROGRESS, AS_OF.minusDays(1), 1));
        issues.add(oldDone("OPS-1"));

        DataQuality q = engine.assess(issues, AS_OF); // 100 - 45 = 55

        assertThat(q.score()).isEqualTo(55);
        assertThat(q.forecastAllowed()).isFalse();
    }

    @Test
    void fixturesHaveOneMissingEstimate() {
        DataQuality q = engine.assess(Fixtures.issues(), LocalDate.of(2026, 10, 27));

        assertThat(only(q, "missing_estimate").count()).isEqualTo(1);
    }

    private static QualityIssue only(DataQuality q, String type) {
        List<QualityIssue> matching = q.issues().stream().filter(i -> i.type().equals(type)).toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }
}
