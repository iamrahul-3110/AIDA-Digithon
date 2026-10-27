package com.aida.engine;

import static com.aida.testdata.TestData.issue;
import static com.aida.testdata.TestData.leave;
import static com.aida.testdata.TestData.throughput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.aida.contract.Absence;
import com.aida.contract.ChangeExplanation;
import com.aida.contract.Driver;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Statuses;
import com.aida.testdata.TestData;

class ExplainEngineTest {

    static final LocalDate TO = LocalDate.of(2026, 10, 27);
    static final LocalDate FROM = TO.minusDays(7);
    final ExplainEngine engine = new ExplainEngine();

    /** Steady history, 16 items in 3.2 before FROM, 4 more added after it, 3 waiting on the client for 8 days. */
    static List<Issue> shop() {
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", TO, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6));
        for (int i = 1; i <= 16; i++) {
            issues.add(issue("SHOP-" + (100 + i)).created(FROM.minusDays(20)).fixVersion("3.2").assignee("Arjun")
                    .build());
        }
        for (int i = 1; i <= 4; i++) {
            issues.add(issue("SHOP-" + (200 + i)).created(FROM.plusDays(2)).fixVersion("3.2").build());
        }
        for (int i = 1; i <= 3; i++) {
            issues.add(issue("SHOP-" + (300 + i)).created(FROM.minusDays(30)).fixVersion("3.2")
                    .moved(Statuses.WAITING_FOR_CLIENT_UAT, TO.minusDays(8)).build());
        }
        return issues;
    }

    @Test
    void scopeAndClientWaitExplainTheShift() {
        ChangeExplanation e = engine.explain(shop(), "3.2", FROM, TO, TestData.SHOP_TEAM, List.of());

        assertThat(e.project()).isEqualTo("SHOP");
        assertThat(e.shiftDays()).isEqualTo((int) java.time.temporal.ChronoUnit.DAYS.between(e.p85From(), e.p85To()));
        assertThat(e.drivers()).extracting(Driver::type).contains("scope_added", "client_wait");
        Driver scope = e.drivers().stream().filter(d -> d.type().equals("scope_added")).findFirst().orElseThrow();
        assertThat(scope.keys()).containsExactly("SHOP-201", "SHOP-202", "SHOP-203", "SHOP-204");
        assertThat(scope.detail()).startsWith("4 items were added to release 3.2");
        assertSumsToShift(e);
        assertSortedByImpact(e);
    }

    @Test
    void addingLeaveForArjunMovesP85LaterAndListsALeaveDriver() {
        List<Absence> arjun = List.of(leave("lv-9", "Arjun", "IN", TO.plusDays(6), TO.plusDays(10)));
        ForecastEngine forecasts = new ForecastEngine();

        Forecast before = forecasts.forecast(shop(), "3.2", TO, TestData.SHOP_TEAM, List.of(), null);
        Forecast after = forecasts.forecast(shop(), "3.2", TO, TestData.SHOP_TEAM, arjun, null);
        ChangeExplanation e = engine.explain(shop(), "3.2", FROM, TO, TestData.SHOP_TEAM, arjun);

        assertThat(after.p85()).isAfterOrEqualTo(before.p85());
        Driver leave = e.drivers().stream().filter(d -> d.type().equals("leave")).findFirst().orElseThrow();
        assertThat(leave.keys()).containsExactly("lv-9");
        assertThat(leave.detail()).contains("Arjun 5");
        assertSumsToShift(e);
    }

    @Test
    void zeroShiftGivesZeroImpacts() {
        // A steady 5 items per week in both windows (the last week's 5 are 3.2 items done after FROM), so the
        // week of work between the dates exactly offsets the week that passed: 11 left at FROM, 6 at TO.
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", TO, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 0));
        for (int i = 1; i <= 5; i++) {
            issues.add(issue("SHOP-D" + i).created(FROM.minusDays(10)).done(FROM.plusDays(1)).fixVersion("3.2").build());
            issues.add(issue("SHOP-O" + i).created(FROM.minusDays(10)).fixVersion("3.2").build());
        }
        issues.add(issue("SHOP-W1").created(FROM.minusDays(10)).fixVersion("3.2")
                .moved(Statuses.WAITING_FOR_CLIENT_UAT, TO.minusDays(8)).build());

        ChangeExplanation e = engine.explain(issues, "3.2", FROM, TO, TestData.SHOP_TEAM, List.of());

        assertThat(e.p85From()).isEqualTo(e.p85To());
        assertThat(e.shiftDays()).isZero();
        assertThat(e.drivers()).extracting(Driver::type).containsExactly("client_wait");
        assertThat(e.drivers()).allSatisfy(d -> assertThat(d.impactDays()).isZero());
    }

    @Test
    void driversAlwaysSumToShiftDays() {
        SplittableRandom random = new SplittableRandom(7);
        for (int round = 0; round < 40; round++) {
            List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", TO, random.ints(12, 0, 10).toArray()));
            int before = random.nextInt(0, 30);
            int added = random.nextInt(0, 10);
            for (int i = 0; i < before; i++) {
                TestData.IssueBuilder b = issue("SHOP-B" + i).created(FROM.minusDays(random.nextInt(1, 60)))
                        .fixVersion("3.2");
                int status = random.nextInt(0, 5);
                if (status > 0) {
                    b.moved(Statuses.WORKFLOW.get(status), TO.minusDays(random.nextInt(0, 30)));
                }
                issues.add(b.build());
            }
            for (int i = 0; i < added; i++) {
                issues.add(issue("SHOP-A" + i).created(FROM.plusDays(random.nextInt(1, 8))).fixVersion("3.2").build());
            }
            List<Absence> absences = random.nextBoolean() ? List.of()
                    : List.of(leave("lv", "Arjun", "IN", TO.plusDays(random.nextInt(0, 40)),
                            TO.plusDays(random.nextInt(40, 50))));

            ChangeExplanation e = engine.explain(issues, "3.2", FROM, TO, TestData.SHOP_TEAM, absences);

            assertSumsToShift(e);
            assertSortedByImpact(e);
        }
    }

    @Test
    void neverMutatesInputs() {
        List<Issue> issues = shop();
        List<Issue> copy = List.copyOf(issues);

        engine.explain(issues, "3.2", FROM, TO, TestData.SHOP_TEAM, List.of());

        assertThat(issues).isEqualTo(copy);
    }

    static void assertSumsToShift(ChangeExplanation e) {
        double sum = e.drivers().stream().mapToDouble(Driver::impactDays).sum();
        if (e.shiftDays() != 0) {
            assertThat(e.drivers()).isNotEmpty();
        }
        assertThat(sum).isCloseTo(e.shiftDays(), within(1e-6));
    }

    static void assertSortedByImpact(ChangeExplanation e) {
        List<Double> impacts = e.drivers().stream().map(d -> Math.abs(d.impactDays())).toList();
        assertThat(impacts).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }
}
