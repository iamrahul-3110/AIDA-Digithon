package com.aida.engine;

import static com.aida.testdata.TestData.leave;
import static com.aida.testdata.TestData.open;
import static com.aida.testdata.TestData.throughput;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aida.contract.Absence;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Statuses;
import com.aida.testdata.TestData;

class ForecastEngineTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    final ForecastEngine engine = new ForecastEngine();

    /** 12 weeks of 4-7 items per week plus {@code remaining} open items in release 3.2. */
    static List<Issue> shop(int remaining) {
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", AS_OF, 4, 6, 5, 7, 4, 5, 6, 5, 7, 4, 6, 5));
        issues.addAll(open("SHOP", "3.2", Statuses.IN_PROGRESS, AS_OF.minusDays(10), remaining));
        return issues;
    }

    @Test
    void identicalInputGivesIdenticalOutput() {
        Forecast a = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));
        Forecast b = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));

        assertThat(a).isEqualTo(b);
        assertThat(a.runs()).isEqualTo(10_000);
        assertThat(a.seed()).isEqualTo(42L);
    }

    @Test
    void recordsWindowAndSampleWeeks() {
        Forecast f = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), null);

        assertThat(f.project()).isEqualTo("SHOP");
        assertThat(f.remainingItems()).isEqualTo(20);
        assertThat(f.sampleWeeks()).isEqualTo(12);
        assertThat(f.windowStart()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(f.windowEnd()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(f.pByTarget()).isNull();
        assertThat(f.p50()).isBeforeOrEqualTo(f.p85());
        assertThat(f.p85()).isBeforeOrEqualTo(f.p95());
    }

    @Test
    void constantThroughputFinishesOnTheExpectedDay() {
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", AS_OF, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5));
        issues.addAll(open("SHOP", "3.2", Statuses.TO_DO, AS_OF.minusDays(3), 20));

        Forecast f = engine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(27));

        // 5 per week: the 4th week (days 21..27) finishes the 20th item on its last day.
        assertThat(f.p50()).isEqualTo(AS_OF.plusDays(27));
        assertThat(f.p95()).isEqualTo(AS_OF.plusDays(27));
        assertThat(f.pByTarget()).isEqualTo(1.0);
        assertThat(f.capacityFactor()).isEqualTo(1.0);
    }

    @Test
    void moreLeaveGivesLaterOrEqualP85() {
        List<Absence> some = List.of(leave("a", "Arjun", "IN", AS_OF.plusDays(6), AS_OF.plusDays(10)));
        List<Absence> more = List.of(leave("a", "Arjun", "IN", AS_OF.plusDays(6), AS_OF.plusDays(10)),
                leave("b", "Priya", "IN", AS_OF.plusDays(6), AS_OF.plusDays(24)),
                leave("c", "Linh", "VN", AS_OF, AS_OF.plusDays(20)));

        Forecast none = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));
        Forecast f1 = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, some, AS_OF.plusDays(25));
        Forecast f2 = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, more, AS_OF.plusDays(25));

        assertThat(f1.p85()).isAfterOrEqualTo(none.p85());
        assertThat(f2.p85()).isAfterOrEqualTo(f1.p85());
        assertThat(f2.p85()).isAfter(none.p85());
        assertThat(f2.capacityFactor()).isLessThan(none.capacityFactor());
    }

    @Test
    void earlierTargetGivesLowerOrEqualPByTarget() {
        double previous = 1.0;
        for (int days = 60; days >= 0; days -= 5) {
            Forecast f = engine.forecast(shop(20), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(),
                    AS_OF.plusDays(days));
            assertThat(f.pByTarget()).isLessThanOrEqualTo(previous);
            previous = f.pByTarget();
        }
        assertThat(previous).isEqualTo(0.0);
    }

    @Test
    void tenThousandRunsUnderOneSecond() {
        List<Issue> issues = shop(60);
        engine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));

        long start = System.nanoTime();
        engine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(1000);
    }

    @Test
    void zeroRemainingFinishesOnAsOf() {
        Forecast f = engine.forecast(shop(0), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));

        assertThat(f.remainingItems()).isZero();
        assertThat(f.p50()).isEqualTo(AS_OF);
        assertThat(f.p85()).isEqualTo(AS_OF);
        assertThat(f.p95()).isEqualTo(AS_OF);
        assertThat(f.pByTarget()).isEqualTo(1.0);
    }

    @Test
    void noThroughputCapsRunsAt104Weeks() {
        List<Issue> issues = open("SHOP", "3.2", Statuses.TO_DO, AS_OF.minusDays(200), 5);

        Forecast f = engine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), AS_OF.plusDays(25));

        assertThat(f.p50()).isEqualTo(AS_OF.plusWeeks(104));
        assertThat(f.pByTarget()).isEqualTo(0.0);
    }

    @Test
    void shortHistoryOnlySamplesWeeksSinceTheFirstIssue() {
        List<Issue> issues = throughput("SHOP", "3.1", AS_OF, 5, 5, 5);

        Forecast f = engine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), null);

        // First issue is created 14 days before the first done week, so 5 weeks of history.
        assertThat(f.sampleWeeks()).isEqualTo(5);
    }

    @Test
    void scopeIsTheReleaseAndDoneItemsAreNotRemaining() {
        List<Issue> issues = new ArrayList<>(shop(5));
        issues.add(TestData.issue("SHOP-900").created(AS_OF.minusDays(20)).done(AS_OF.minusDays(2)).fixVersion("3.2")
                .build());
        issues.add(TestData.issue("SHOP-901").created(AS_OF.minusDays(20)).fixVersion("3.3").build());

        assertThat(engine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), null).remainingItems())
                .isEqualTo(5);
    }
}
