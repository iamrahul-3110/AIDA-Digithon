package com.aida.engine;

import static com.aida.testdata.TestData.issue;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aida.contract.Issue;
import com.aida.contract.Statuses;
import com.aida.testdata.Fixtures;

class SnapshotsTest {

    static final LocalDate D = LocalDate.of(2026, 10, 15);

    @Test
    void issueResolvedAfterDIsUnresolvedAtDWithItsStatusThen() {
        Issue i = issue("SHOP-1").created(D.minusDays(10)).moved(Statuses.IN_PROGRESS, D.minusDays(5))
                .moved(Statuses.QA, D.minusDays(1)).done(D.plusDays(3)).build();

        Issue s = only(Snapshots.snapshot(List.of(i), D));

        assertThat(s.status()).isEqualTo(Statuses.QA);
        assertThat(s.resolved()).isNull();
        assertThat(s.statusHistory()).hasSize(3);
    }

    @Test
    void issueCreatedAfterDIsAbsent() {
        Issue later = issue("SHOP-2").created(D.plusDays(1)).build();
        Issue sameDay = issue("SHOP-3").created(D).build();

        assertThat(Snapshots.snapshot(List.of(later, sameDay), D)).extracting(Issue::key).containsExactly("SHOP-3");
    }

    @Test
    void doneOnDIsResolvedOnD() {
        Issue i = issue("SHOP-4").created(D.minusDays(3)).done(Instant.parse("2026-10-15T23:59:00Z")).build();

        Issue s = only(Snapshots.snapshot(List.of(i), D));

        assertThat(s.status()).isEqualTo(Statuses.DONE);
        assertThat(s.resolved()).isEqualTo(D);
    }

    @Test
    void reopenedIssueIsUnresolvedAfterReopenAndResolvedAtLastDone() {
        Issue i = issue("SHOP-5").created(D.minusDays(20)).done(D.minusDays(10))
                .moved(Statuses.IN_PROGRESS, D.minusDays(5)).done(D.plusDays(2)).build();

        Issue atD = only(Snapshots.snapshot(List.of(i), D));
        assertThat(atD.status()).isEqualTo(Statuses.IN_PROGRESS);
        assertThat(atD.resolved()).isNull();

        Issue beforeReopen = only(Snapshots.snapshot(List.of(i), D.minusDays(7)));
        assertThat(beforeReopen.status()).isEqualTo(Statuses.DONE);
        assertThat(beforeReopen.resolved()).isEqualTo(D.minusDays(10));

        Issue after = only(Snapshots.snapshot(List.of(i), D.plusDays(5)));
        assertThat(after.resolved()).isEqualTo(D.plusDays(2));
    }

    @Test
    void neverMutatesInputs() {
        List<Issue> fixtures = Fixtures.issues();
        List<Issue> copy = new ArrayList<>(fixtures);

        Snapshots.snapshot(fixtures, LocalDate.of(2026, 9, 20));

        assertThat(fixtures).isEqualTo(copy);
    }

    @Test
    void fixturesAtTheirAsOfKeepEveryIssueAndStatus() {
        List<Issue> fixtures = Fixtures.issues();

        List<Issue> s = Snapshots.snapshot(fixtures, LocalDate.of(2026, 10, 27));

        assertThat(s).extracting(Issue::status).containsExactlyElementsOf(fixtures.stream().map(Issue::status).toList());
        assertThat(s).extracting(Issue::resolved)
                .containsExactlyElementsOf(fixtures.stream().map(Issue::resolved).toList());
    }

    private static Issue only(List<Issue> issues) {
        assertThat(issues).hasSize(1);
        return issues.get(0);
    }
}
