package com.aida.engine;

import static com.aida.testdata.TestData.issue;
import static com.aida.testdata.TestData.throughput;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Statuses;
import com.aida.contract.WhatIf;
import com.aida.testdata.TestData;

class WhatIfEngineTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    static final LocalDate TARGET = AS_OF.plusDays(25);
    final WhatIfEngine engine = new WhatIfEngine();
    final ForecastEngine forecastEngine = new ForecastEngine();

    /** 20 open 3.2 items, 6 of them in epic SHOP-E7 "Gift cards" (one of those already Done). */
    static List<Issue> shop() {
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", AS_OF, 4, 6, 5, 7, 4, 5, 6, 5, 7, 4, 6, 5));
        for (int i = 1; i <= 20; i++) {
            TestData.IssueBuilder b = issue("SHOP-" + (100 + i)).created(AS_OF.minusDays(30)).fixVersion("3.2");
            if (i <= 6) {
                b.epic("SHOP-E7", "Gift cards");
            }
            issues.add(b.build());
        }
        issues.add(issue("SHOP-99").created(AS_OF.minusDays(30)).done(AS_OF.minusDays(2)).fixVersion("3.2")
                .epic("SHOP-E7", "Gift cards").build());
        return issues;
    }

    static WhatIf whatIf(List<String> drop, List<Person> add, List<String> remove) {
        return new WhatIf(drop, add, remove, TARGET);
    }

    @Test
    void noChangeEqualsThePlainForecast() {
        Forecast plain = forecastEngine.forecast(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), TARGET);

        assertThat(engine.run(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), whatIf(null, null, null)))
                .isEqualTo(plain);
    }

    @Test
    void droppingAnEpicRemovesItsOpenItemsByKeyOrName() {
        Forecast byKey = engine.run(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(),
                whatIf(List.of("SHOP-E7"), null, null));
        Forecast byName = engine.run(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(),
                whatIf(List.of("gift cards"), null, null));

        assertThat(byKey.remainingItems()).isEqualTo(14);
        assertThat(byName).isEqualTo(byKey);
    }

    @Test
    void droppingAnEpicNeverLowersPByTarget() {
        SplittableRandom random = new SplittableRandom(1);
        for (int round = 0; round < 25; round++) {
            List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", AS_OF,
                    random.ints(12, 0, 9).toArray()));
            int open = random.nextInt(5, 40);
            for (int i = 0; i < open; i++) {
                TestData.IssueBuilder b = issue("SHOP-" + (500 + i)).created(AS_OF.minusDays(20)).fixVersion("3.2");
                b.epic("SHOP-E" + random.nextInt(1, 4), "Epic");
                issues.add(b.build());
            }
            LocalDate target = AS_OF.plusDays(random.nextInt(7, 90));
            Forecast base = forecastEngine.forecast(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), target);
            Forecast dropped = engine.run(issues, "3.2", AS_OF, TestData.SHOP_TEAM, List.of(),
                    new WhatIf(List.of("SHOP-E" + random.nextInt(1, 4)), null, null, target));

            assertThat(dropped.pByTarget()).isGreaterThanOrEqualTo(base.pByTarget());
            assertThat(dropped.p85()).isBeforeOrEqualTo(base.p85());
        }
    }

    @Test
    void addingPeopleIsFasterAndRemovingIsSlower() {
        Forecast base = engine.run(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(), whatIf(null, null, null));
        Forecast more = engine.run(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(),
                whatIf(null, List.of(new Person("Sam", "IN"), new Person("Kim", "KR"), new Person("An", "VN")), null));
        Forecast fewer = engine.run(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, List.of(),
                whatIf(null, null, List.of("Arjun", "priya", "Rahul")));

        assertThat(more.p85()).isBefore(base.p85());
        assertThat(fewer.p85()).isAfter(base.p85());
    }

    @Test
    void neverMutatesInputs() {
        List<Issue> issues = shop();
        List<Issue> issuesCopy = List.copyOf(issues);
        List<Person> team = new ArrayList<>(TestData.SHOP_TEAM);

        engine.run(issues, "3.2", AS_OF, team, List.of(),
                whatIf(List.of("SHOP-E7"), List.of(new Person("Sam", "IN")), List.of("Arjun")));

        assertThat(issues).isEqualTo(issuesCopy);
        assertThat(team).isEqualTo(TestData.SHOP_TEAM);
        assertThat(issues).filteredOn(i -> !Statuses.DONE.equals(i.status())).hasSize(20);
    }
}
