package com.aida.mcp;

import static com.aida.testdata.TestData.issue;
import static com.aida.testdata.TestData.throughput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aida.contract.Issue;
import com.aida.contract.Release;
import com.aida.contract.Statuses;
import com.aida.testdata.TestData;

/** AnalysisTools end to end inside the app, with ProjectData replaced by in-memory data around today. */
@SpringBootTest
class AnalysisToolsTest {

    static final LocalDate TODAY = LocalDate.now();

    @MockitoBean
    ProjectData projectData;

    @Autowired
    AnalysisTools tools;

    @Autowired
    ToolCallbackProvider aidaTools;

    @BeforeEach
    void data() {
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", TODAY, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6));
        for (int i = 1; i <= 20; i++) {
            TestData.IssueBuilder b = issue("SHOP-" + (100 + i)).created(TODAY.minusDays(i <= 16 ? 30 : 3))
                    .fixVersion("3.2").assignee("Arjun");
            if (i <= 6) {
                b.epic("SHOP-E7", "Gift cards");
            }
            if (i == 7) {
                b.moved(Statuses.WAITING_FOR_CLIENT_UAT, TODAY.minusDays(8));
            }
            issues.add(b.build());
        }
        List<Release> releases = List.of(new Release("SHOP", "3.1", TODAY.minusDays(60), true),
                new Release("SHOP", "3.2", TODAY.plusDays(25), false));
        when(projectData.load(eq("SHOP"), any()))
                .thenReturn(new ProjectData.Data(issues, TestData.SHOP_TEAM, List.of(), releases));
    }

    @Test
    void toolsAreRegistered() {
        assertThat(Arrays.stream(aidaTools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .contains("explain_change", "run_what_if", "get_risks");
    }

    @Test
    void explainChangeReturnsDriversAndMeta() {
        Map<String, Object> result = tools.explainChange("SHOP", "3.2", TODAY.minusDays(7).toString(),
                TODAY.toString());

        assertThat(result).containsKeys("shiftDays", "p85From", "p85To", "drivers");
        assertThat(result.get("drivers")).asList().isNotEmpty();
        assertThat(result.get("meta")).isInstanceOfSatisfying(Map.class, meta -> assertThat(meta)
                .containsEntry("tool", "explain_change")
                .containsEntry("windowStart", com.aida.engine.ForecastEngine.windowStart(TODAY.minusDays(7))
                        .toString())
                .containsEntry("windowEnd", com.aida.engine.ForecastEngine.windowEnd(TODAY).toString()));
    }

    @Test
    void runWhatIfDefaultsTargetToReleaseDateAndDropsEpic() {
        Map<String, Object> base = tools.runWhatIf("SHOP", "3.2", null, null, null, null);
        Map<String, Object> dropped = tools.runWhatIf("SHOP", "3.2", List.of("Gift cards"), null, null, null);

        assertThat(base).containsEntry("targetDate", TODAY.plusDays(25).toString())
                .containsEntry("remainingItems", 20);
        assertThat(dropped).containsEntry("remainingItems", 14);
        assertThat((Double) dropped.get("pByTarget")).isGreaterThanOrEqualTo((Double) base.get("pByTarget"));
        assertThat(dropped.get("meta")).isInstanceOfSatisfying(Map.class,
                meta -> assertThat(meta).containsEntry("tool", "run_what_if"));
    }

    @Test
    void getRisksListsTheClientWait() {
        Map<String, Object> result = tools.getRisks("SHOP");

        assertThat(result).containsEntry("project", "SHOP");
        assertThat(result.get("items")).asList().anySatisfy(item -> assertThat(item).isInstanceOfSatisfying(
                Map.class, m -> assertThat(m).containsEntry("key", "SHOP-107").containsEntry("reason", "client_wait")));
    }

    @Test
    void unknownReleaseIsAClearError() {
        assertThatThrownBy(() -> tools.runWhatIf("SHOP", "9.9", null, null, null, null))
                .hasMessageContaining("Unknown release 9.9").hasMessageContaining("3.2");
    }

    // TODO(after m4-e2e): token without SHOP gets "Access denied: token '<name>' cannot read project SHOP",
    // using Person A's security test helpers.
}
