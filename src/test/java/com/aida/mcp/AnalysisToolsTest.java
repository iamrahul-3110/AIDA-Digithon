package com.aida.mcp;

import static com.aida.testdata.TestData.issue;
import static com.aida.testdata.TestData.throughput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aida.contract.Issue;
import com.aida.contract.Release;
import com.aida.contract.Statuses;
import com.aida.engine.ForecastEngine;
import com.aida.security.TestTokens;
import com.aida.testdata.TestData;

/**
 * AnalysisTools inside the app, through the real ToolSupport access check, with ProjectData replaced by in-memory
 * data around today. Calls run as the full test token unless a test says otherwise.
 */
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
        runAs("test-full", "SHOP", "OPS");
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

    @AfterEach
    void clearToken() {
        SecurityContextHolder.clearContext();
    }

    static void runAs(String name, String... projects) {
        SecurityContextHolder.getContext()
                .setAuthentication(TestTokens.authentication(name, Set.of(projects), Set.of("mcp")));
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
                .containsEntry("windowStart", ForecastEngine.windowStart(TODAY.minusDays(7)).toString())
                .containsEntry("windowEnd", ForecastEngine.windowEnd(TODAY).toString()));
    }

    @Test
    void runWhatIfDefaultsTargetToReleaseDateAndDropsEpic() {
        Map<String, Object> base = tools.runWhatIf("SHOP", "3.2", null, null, null, null);
        Map<String, Object> dropped = tools.runWhatIf("shop", "3.2", List.of("Gift cards"), null, null, null);

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
    void unknownReleaseAndBadDatesAreClearErrors() {
        assertThatThrownBy(() -> tools.runWhatIf("SHOP", "9.9", null, null, null, null))
                .hasMessageContaining("Unknown release '9.9'").hasMessageContaining("3.2");
        assertThatThrownBy(() -> tools.explainChange("SHOP", "3.2", "last week", TODAY.toString()))
                .hasMessageContaining("fromDate must be a date like");
        assertThatThrownBy(() -> tools.explainChange("SHOP", "3.2", TODAY.toString(), TODAY.toString()))
                .hasMessageContaining("fromDate must be before toDate");
    }

    @Test
    void tokenWithoutTheProjectIsDeniedByEveryTool() {
        runAs("test-ops-only", "OPS");
        String denied = "Access denied: token 'test-ops-only' cannot read project SHOP";

        List<Executable> calls = List.of(
                () -> tools.explainChange("SHOP", "3.2", TODAY.minusDays(7).toString(), TODAY.toString()),
                () -> tools.runWhatIf("SHOP", "3.2", List.of("SHOP-E7"), null, null, null),
                () -> tools.getRisks("shop"));
        for (Executable call : calls) {
            assertThatThrownBy(call::execute).isInstanceOf(ToolSupport.AccessDenied.class).hasMessage(denied);
        }
        verify(projectData, never()).load(anyString(), any());
    }

    @Test
    void noTokenIsDenied() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> tools.getRisks("SHOP")).isInstanceOf(ToolSupport.AccessDenied.class)
                .hasMessage("Access denied: no token");
    }
}
