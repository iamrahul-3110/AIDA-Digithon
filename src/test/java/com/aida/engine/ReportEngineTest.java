package com.aida.engine;

import static com.aida.testdata.TestData.issue;
import static com.aida.testdata.TestData.leave;
import static com.aida.testdata.TestData.throughput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Statuses;
import com.aida.testdata.TestData;

import tools.jackson.databind.json.JsonMapper;

class ReportEngineTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    static final JsonMapper JSON = JsonMapper.builder().build();
    final ReportEngine engine = new ReportEngine();

    /** SHOP 3.2 with every team member assigned, names in summaries, stale team items and client waits. */
    static List<Issue> shop() {
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", AS_OF, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6));
        List<Person> team = TestData.SHOP_TEAM;
        for (int i = 0; i < 18; i++) {
            String name = team.get(i % team.size()).name();
            TestData.IssueBuilder b = issue("SHOP-" + (100 + i)).created(AS_OF.minusDays(i < 14 ? 30 : 3))
                    .fixVersion("3.2").assignee(name).summary("Checkout step owned by " + name);
            if (i < 4) {
                b.moved(Statuses.IN_PROGRESS, AS_OF.minusDays(20));
            } else if (i < 7) {
                b.moved(Statuses.WAITING_FOR_CLIENT_UAT, AS_OF.minusDays(6 + i - 4));
            } else if (i < 9) {
                b.done(AS_OF.minusDays(3));
            }
            issues.add(b.build());
        }
        return issues;
    }

    static final List<Absence> ABSENCES = List.of(leave("lv-1", "Arjun", "IN", AS_OF.plusDays(6), AS_OF.plusDays(10)),
            leave("lv-2", "Ji-woo", "KR", AS_OF.plusDays(13), AS_OF.plusDays(14)));

    Map<String, Object> report(String audience) {
        return engine.build(shop(), "3.2", AS_OF, TestData.SHOP_TEAM, ABSENCES, audience);
    }

    @Test
    void teamReportHasEverything() {
        Map<String, Object> r = report("team");

        assertThat(r).containsOnlyKeys("audience", "project", "release", "asOf", "forecast", "progress", "topDrivers",
                "risks", "asks");
        assertThat(r.get("progress")).isEqualTo(Map.of("done", 2L, "remaining", 16L, "total", 18));
        assertThat((List<?>) r.get("topDrivers")).hasSizeLessThanOrEqualTo(3).isNotEmpty();
        assertThat((List<?>) r.get("risks")).hasSize(5);
        assertThat((List<?>) r.get("asks")).hasSize(3);
        assertThat(JSON.writeValueAsString(r)).contains("Arjun");
    }

    @Test
    void managementMatchesTeam() {
        Map<String, Object> team = report("team");
        Map<String, Object> management = report("Management");

        team.remove("audience");
        management.remove("audience");
        assertThat(management).isEqualTo(team);
    }

    @Test
    void clientReportHasNoTeamNamesAndNoTeamSideRisks() {
        Map<String, Object> r = report("client");

        String json = JSON.writeValueAsString(r);
        for (Person p : TestData.SHOP_TEAM) {
            assertThat(json).doesNotContainIgnoringCase(p.name());
        }
        for (Person p : TestData.OPS_TEAM) {
            assertThat(json).doesNotContainIgnoringCase(p.name());
        }
        assertThat(json).doesNotContain("assignee");
        assertThat((List<Map<String, Object>>) r.get("risks")).isNotEmpty()
                .allSatisfy(risk -> assertThat(risk).containsEntry("side", "client"));
        assertThat((List<Map<String, Object>>) r.get("asks")).extracting(a -> a.get("key"))
                .containsExactly("SHOP-104", "SHOP-105", "SHOP-106");
    }

    @Test
    void clientReportForOpsHasNoOpsNames() {
        List<Issue> ops = new ArrayList<>(throughput("OPS", "Q4", AS_OF, 3, 3, 3, 3, 3, 3, 3, 3));
        ops.add(issue("OPS-1").created(AS_OF.minusDays(40)).fixVersion("Q4").assignee("Meera")
                .moved(Statuses.WAITING_FOR_CLIENT_UAT, AS_OF.minusDays(9)).summary("Meera's runbook review").build());
        ops.add(issue("OPS-2").created(AS_OF.minusDays(40)).fixVersion("Q4").assignee("Minh")
                .moved(Statuses.IN_PROGRESS, AS_OF.minusDays(30)).build());

        String json = JSON.writeValueAsString(
                engine.build(ops, "Q4", AS_OF, TestData.OPS_TEAM, List.of(), "client"));

        assertThat(json).doesNotContainIgnoringCase("Meera").doesNotContainIgnoringCase("Minh");
        assertThat(json).contains("a team member's runbook review");
    }

    @Test
    void hqReportHasOnlyTheAllowedKeys() {
        Map<String, Object> r = report("hq");

        assertThat(r.keySet()).containsExactlyInAnyOrderElementsOf(ReportEngine.HQ_KEYS);
        assertThat((List<?>) r.get("topDrivers")).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void unknownAudienceIsRejected() {
        assertThatThrownBy(() -> report("press")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team, management, client, hq");
    }

    @Test
    void scrubKeepsWordsThatOnlyContainAName() {
        assertThat(ReportEngine.scrubText("Linh and Linhart; MIN-JUN asked", List.of("Min-jun", "Linh")))
                .isEqualTo("a team member and Linhart; a team member asked");
    }
}
