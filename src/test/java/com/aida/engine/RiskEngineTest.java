package com.aida.engine;

import static com.aida.testdata.TestData.issue;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aida.contract.Issue;
import com.aida.contract.Risk;
import com.aida.contract.RiskReport;
import com.aida.contract.Statuses;
import com.aida.testdata.Fixtures;

class RiskEngineTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    final RiskEngine engine = new RiskEngine();

    static Issue in(String key, String status, int daysAgo) {
        return issue(key).created(AS_OF.minusDays(daysAgo + 5)).moved(status, AS_OF.minusDays(daysAgo))
                .assignee("Arjun").build();
    }

    @Test
    void reasonsAndSidesFollowTheRules() {
        RiskReport r = engine.assess(List.of(
                in("SHOP-1", Statuses.WAITING_FOR_CLIENT_UAT, 6), // client_wait
                in("SHOP-2", Statuses.WAITING_FOR_CLIENT_UAT, 4), // too short
                in("SHOP-3", Statuses.IN_PROGRESS, 20), // stale
                in("SHOP-4", Statuses.IN_REVIEW, 9), // aging: median of 1, 2, 9 is 2
                in("SHOP-5", Statuses.IN_REVIEW, 2),
                in("SHOP-6", Statuses.IN_REVIEW, 1),
                in("SHOP-7", Statuses.TO_DO, 60)), AS_OF); // not active

        assertThat(r.project()).isEqualTo("SHOP");
        assertThat(r.items()).extracting(Risk::key).containsExactly("SHOP-3", "SHOP-4", "SHOP-1");
        assertThat(r.items()).extracting(Risk::reason).containsExactly("stale", "aging", "client_wait");
        assertThat(r.items()).extracting(Risk::side).containsExactly("team", "team", "client");
        assertThat(r.items()).extracting(Risk::days).containsExactly(20, 9, 6);
        assertThat(r.items().get(0).assignee()).isEqualTo("Arjun");
    }

    @Test
    void bottleneckIsTheActiveStatusWithMostItemDays() {
        RiskReport r = engine.assess(List.of(
                in("SHOP-1", Statuses.QA, 3), in("SHOP-2", Statuses.QA, 3), in("SHOP-3", Statuses.QA, 3),
                in("SHOP-4", Statuses.IN_PROGRESS, 8), in("SHOP-5", Statuses.TO_DO, 90)), AS_OF);

        assertThat(r.bottleneckStage()).isEqualTo(Statuses.QA);
    }

    @Test
    void noActiveItemsMeansNoBottleneck() {
        RiskReport r = engine.assess(List.of(in("SHOP-1", Statuses.TO_DO, 3)), AS_OF);

        assertThat(r.bottleneckStage()).isNull();
        assertThat(r.items()).isEmpty();
    }

    @Test
    void fixtureUatItemIsAClientWait() {
        RiskReport r = engine.assess(Fixtures.issues(), AS_OF);

        assertThat(r.items()).filteredOn(i -> i.key().equals("SHOP-106")).singleElement()
                .satisfies(i -> {
                    assertThat(i.reason()).isEqualTo("client_wait");
                    assertThat(i.side()).isEqualTo("client");
                    assertThat(i.days()).isEqualTo(8);
                });
    }
}
