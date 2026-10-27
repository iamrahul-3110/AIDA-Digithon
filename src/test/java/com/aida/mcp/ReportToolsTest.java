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
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Release;
import com.aida.contract.Statuses;
import com.aida.engine.ReportEngine;
import com.aida.security.TestTokens;
import com.aida.testdata.TestData;

import tools.jackson.databind.json.JsonMapper;

/**
 * ReportTools inside the app, through the real ToolSupport access check, with ProjectData replaced by in-memory
 * data around today. Calls run as the full test token unless a test says otherwise.
 */
@SpringBootTest
class ReportToolsTest {

    static final LocalDate TODAY = LocalDate.now();

    @MockitoBean
    ProjectData projectData;

    @Autowired
    ReportTools tools;

    @Autowired
    ToolCallbackProvider aidaTools;

    @Autowired
    JsonMapper mapper;

    @BeforeEach
    void data() {
        runAs("test-full", "SHOP", "OPS");
        List<Issue> issues = new ArrayList<>(throughput("SHOP", "3.1", TODAY, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6));
        for (int i = 0; i < 12; i++) {
            String name = TestData.SHOP_TEAM.get(i % 6).name();
            TestData.IssueBuilder b = issue("SHOP-" + (100 + i)).created(TODAY.minusDays(30)).fixVersion("3.2")
                    .assignee(name);
            if (i < 3) {
                b.moved(Statuses.WAITING_FOR_CLIENT_UAT, TODAY.minusDays(7));
            } else if (i < 6) {
                b.moved(Statuses.IN_PROGRESS, TODAY.minusDays(20));
            }
            issues.add(b.build());
        }
        List<Release> releases = List.of(new Release("SHOP", "3.2", TODAY.plusDays(25), false));
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
    void buildReportIsRegistered() {
        assertThat(Arrays.stream(aidaTools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .contains("build_report");
    }

    @Test
    void forecastUsesTheReleaseDateAndMetaHasTheWindow() {
        Map<String, Object> result = tools.buildReport("shop", "3.2", "management");

        assertThat(result.get("forecast")).isInstanceOfSatisfying(Map.class, f -> assertThat(f)
                .containsEntry("targetDate", TODAY.plusDays(25).toString()).containsKey("pByTarget"));
        assertThat(result.get("meta")).isInstanceOfSatisfying(Map.class, meta -> assertThat(meta)
                .containsEntry("tool", "build_report").containsKey("windowStart"));
    }

    @Test
    void clientReportHasNoTeamNames() {
        String json = mapper.writeValueAsString(tools.buildReport("SHOP", "3.2", "client"));

        for (Person p : TestData.SHOP_TEAM) {
            assertThat(json).doesNotContainIgnoringCase(p.name());
        }
    }

    @Test
    void hqReportHasOnlyTheAllowedKeysPlusMeta() {
        Map<String, Object> result = tools.buildReport("SHOP", "3.2", "hq");

        assertThat(result.keySet()).containsAll(ReportEngine.HQ_KEYS)
                .doesNotContain("risks", "asks");
    }

    @Test
    void tokenWithoutTheProjectIsDenied() {
        runAs("test-ops-only", "OPS");

        assertThatThrownBy(() -> tools.buildReport("SHOP", "3.2", "client"))
                .isInstanceOf(ToolSupport.AccessDenied.class)
                .hasMessage("Access denied: token 'test-ops-only' cannot read project SHOP");
        verify(projectData, never()).load(anyString(), any());
    }
}
