package com.aida.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Statuses;
import com.aida.mcp.ProjectData;
import com.aida.mock.Dataset;
import com.aida.mock.MockStore;

/** The HTTP adapters against the running mock: their output equals the generator's dataset, field for field. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HttpAdaptersTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    MockStore store;

    HttpJiraSource jira;
    HttpLeaveSource leave;
    Dataset data;

    @BeforeEach
    void setUp() {
        data = store.reset();
        jira = new HttpJiraSource("http://localhost:" + port + "/mock/jira");
        leave = new HttpLeaveSource("http://localhost:" + port + "/mock");
    }

    List<Issue> expected(String project, String fixVersion) {
        return data.issues().stream()
                .filter(i -> i.project().equals(project) && (fixVersion == null || fixVersion.equals(i.fixVersion())))
                .sorted(Comparator.comparingInt(i -> Integer.parseInt(i.key().substring(i.key().indexOf('-') + 1))))
                .toList();
    }

    @Test
    void releaseIssuesMatchTheDatasetIncludingHistory() {
        List<Issue> actual = jira.issues("SHOP", "3.2");
        assertThat(actual).isNotEmpty().containsExactlyElementsOf(expected("SHOP", "3.2"));
    }

    @Test
    void wholeProjectIsPagedPastOneHundred() {
        List<Issue> expected = expected("SHOP", null);
        assertThat(expected).hasSizeGreaterThan(HttpJiraSource.PAGE_SIZE);
        assertThat(jira.issues("SHOP", null)).containsExactlyElementsOf(expected);
    }

    @Test
    void opsIssuesKeepMissingEstimatesAndTheBulkClose() {
        List<Issue> actual = jira.issues("OPS", null);
        assertThat(actual).containsExactlyElementsOf(expected("OPS", null));
        assertThat(actual).anyMatch(i -> i.type().equals("Story") && i.estimate() == null);
    }

    @Test
    void handfulOfIssuesMappedFieldByField() {
        for (Issue want : List.of(firstWithStatus(Statuses.DONE), firstWithStatus(Statuses.WAITING_FOR_CLIENT_UAT),
                firstWithStatus(Statuses.TO_DO), firstWithStatus(Statuses.IN_PROGRESS))) {
            Issue got = jira.issues("SHOP", want.fixVersion()).stream()
                    .filter(i -> i.key().equals(want.key())).findFirst().orElseThrow();
            assertThat(got.project()).isEqualTo(want.project());
            assertThat(got.type()).isEqualTo(want.type());
            assertThat(got.summary()).isEqualTo(want.summary());
            assertThat(got.status()).isEqualTo(want.status());
            assertThat(got.statusHistory()).isEqualTo(want.statusHistory());
            assertThat(got.statusHistory().getFirst().status()).isEqualTo(Statuses.TO_DO);
            assertThat(got.created()).isEqualTo(want.created());
            assertThat(got.resolved()).isEqualTo(want.resolved());
            assertThat(got.estimate()).isEqualTo(want.estimate());
            assertThat(got.epic()).isEqualTo(want.epic());
            assertThat(got.epicName()).isEqualTo(want.epicName());
            assertThat(got.fixVersion()).isEqualTo(want.fixVersion());
            assertThat(got.assignee()).isEqualTo(want.assignee());
        }
    }

    Issue firstWithStatus(String status) {
        return expected("SHOP", null).stream().filter(i -> i.status().equals(status)).findFirst().orElseThrow();
    }

    @Test
    void releases() {
        assertThat(jira.releases("SHOP")).containsExactlyElementsOf(
                data.versions().stream().filter(r -> r.project().equals("SHOP")).toList());
        assertThat(jira.releases("NOPE")).isEmpty();
    }

    @Test
    void teamAndAbsences() {
        assertThat(leave.team("SHOP")).isEqualTo(data.team().get("SHOP"));
        assertThat(leave.team("OPS")).isEqualTo(data.team().get("OPS"));

        LocalDate from = store.asOf().minusWeeks(12);
        LocalDate to = store.asOf().plusMonths(3);
        assertThat(leave.absences(from, to)).containsExactlyInAnyOrderElementsOf(data.absences().stream()
                .filter(a -> !a.end().isBefore(from) && !a.start().isAfter(to)).toList());
    }

    @Test
    void addedLeaveIsVisibleThroughTheAdapter() {
        Absence added = store.addLeave(new Person("Arjun", "IN"), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6));
        assertThat(leave.absences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).contains(added);
    }

    @Test
    void projectDataLoadsTheEngineWindow() {
        LocalDate asOf = store.asOf();
        ProjectData.Data shop = new ProjectData(jira, leave).load("SHOP", asOf);

        assertThat(shop.issues()).containsExactlyElementsOf(expected("SHOP", null));
        assertThat(shop.team()).isEqualTo(data.team().get("SHOP"));
        assertThat(shop.releases()).extracting(r -> r.name()).containsExactly("3.1", "3.2", "3.3");
        // Team leaves and holidays in team countries only: Meera (OPS) is out, IN/KR/VN holidays are in.
        assertThat(shop.absences()).isNotEmpty()
                .allMatch(a -> a.person() == null || List.of("Arjun", "Priya", "Rahul", "Ji-woo", "Min-jun", "Linh")
                        .contains(a.person()))
                .noneMatch(a -> "Meera".equals(a.person()))
                .allMatch(a -> !a.end().isBefore(asOf.minusWeeks(12)) && !a.start().isAfter(asOf.plusMonths(6)));

        ProjectData.Data ops = new ProjectData(jira, leave).load("OPS", asOf);
        assertThat(ops.absences()).anyMatch(a -> "Meera".equals(a.person()))
                .allMatch(a -> a.person() != null || List.of("IN", "VN").contains(a.country()));
    }
}
