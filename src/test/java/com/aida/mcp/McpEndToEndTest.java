package com.aida.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aida.contract.DataQuality;
import com.aida.contract.Forecast;
import com.aida.contract.QualityIssue;
import com.aida.engine.ForecastEngine;
import com.aida.engine.QualityEngine;
import com.aida.mock.MockStore;
import com.aida.security.TestTokens;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real MCP protocol over HTTP with the MCP Java SDK client: bearer token -> /mcp -> tools -> ProjectData over the
 * HTTP adapters -> mock. The engines are Person B's, so they are replaced by Mockito beans here.
 */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18081",
        "aida.jira.base-url=http://localhost:18081/mock/jira",
        "aida.leave.base-url=http://localhost:18081/mock"})
class McpEndToEndTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);

    @MockitoBean
    ForecastEngine forecastEngine;

    @MockitoBean
    QualityEngine qualityEngine;

    @Autowired
    MockStore store;

    @Autowired
    AuditLog auditLog;

    @Autowired
    JsonMapper mapper;

    McpSyncClient client;

    McpSyncClient connect(String token) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:18081")
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build();
        client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
        client.initialize();
        return client;
    }

    @AfterEach
    void close() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> json(CallToolResult result) {
        return mapper.readValue(text(result), Map.class);
    }

    @Test
    void validTokenGetsForecastJsonWithMeta() {
        LocalDate target = store.asOf().plusDays(25);
        Forecast forecast = new Forecast("SHOP", "3.2", AS_OF, 18, target.minusDays(2), target.plusDays(10),
                target.plusDays(16), target, 0.45, 12, AS_OF.minusWeeks(12), AS_OF, 0.94, 10_000, 42L);
        when(forecastEngine.forecast(any(), eq("3.2"), eq(AS_OF), any(), any(), eq(target))).thenReturn(forecast);

        CallToolResult result = connect(TestTokens.FULL).callTool(new CallToolRequest("forecast_delivery",
                Map.of("project", "SHOP", "release", "3.2", "asOf", AS_OF.toString())));

        assertThat(result.isError()).as(text(result)).isNotEqualTo(Boolean.TRUE);
        Map<String, Object> body = json(result);
        assertThat(body).containsEntry("pByTarget", 0.45).containsEntry("p85", target.plusDays(10).toString())
                .containsEntry("targetDate", target.toString());
        assertThat((Map<String, Object>) body.get("meta")).containsEntry("tool", "forecast_delivery")
                .containsEntry("windowStart", AS_OF.minusWeeks(12).toString())
                .containsEntry("windowEnd", AS_OF.toString())
                .containsKey("calledAt");

        // The tool fed the engine the real project data from the mock, through the HTTP adapters.
        verify(forecastEngine).forecast(org.mockito.ArgumentMatchers.argThat(issues -> issues.size() == store.dataset()
                .issues().stream().filter(i -> i.project().equals("SHOP")).count()), eq("3.2"), eq(AS_OF),
                org.mockito.ArgumentMatchers.argThat(team -> team.size() == 6), any(), eq(target));
    }

    @Test
    void tokenWithoutTheProjectGetsAccessDeniedToolError() {
        CallToolResult result = connect(TestTokens.OPS_ONLY).callTool(new CallToolRequest("forecast_delivery",
                Map.of("project", "SHOP", "release", "3.2")));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("Access denied: token 'test-ops-only' cannot read project SHOP");
    }

    @Test
    void checkDataQualityThroughMcp() {
        when(qualityEngine.assess(any(), any())).thenReturn(new DataQuality("OPS", 42,
                List.of(new QualityIssue("stale_status", 6, List.of("OPS-1"), "6 of 20 active items (30%) are stale.")),
                false));

        CallToolResult result = connect(TestTokens.OPS_ONLY).callTool(new CallToolRequest("check_data_quality",
                Map.of("project", "OPS")));

        assertThat(result.isError()).as(text(result)).isNotEqualTo(Boolean.TRUE);
        assertThat(json(result)).containsEntry("score", 42).containsEntry("forecastAllowed", false);
    }

    @Test
    void unknownReleaseIsAToolError() {
        CallToolResult result = connect(TestTokens.FULL).callTool(new CallToolRequest("forecast_delivery",
                Map.of("project", "SHOP", "release", "9.9", "asOf", AS_OF.toString())));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("Unknown release '9.9'");
    }

    @Test
    void listsTheForecastTools() {
        assertThat(connect(TestTokens.FULL).listTools().tools()).extracting(t -> t.name())
                .contains("forecast_delivery", "check_data_quality");
    }

    @Test
    void auditLogNamesTheTokenNeverTheToken() throws Exception {
        connect(TestTokens.OPS_ONLY).callTool(new CallToolRequest("forecast_delivery",
                Map.of("project", "SHOP", "release", "3.2")));

        List<String> lines = Files.readAllLines(auditLog.file());
        @SuppressWarnings("unchecked")
        Map<String, Object> last = mapper.readValue(lines.getLast(), Map.class);
        assertThat(last).containsKeys("ts", "tokenName", "tool", "args", "resultSummary")
                .containsEntry("tokenName", "test-ops-only")
                .containsEntry("tool", "forecast_delivery");
        assertThat((String) last.get("resultSummary")).startsWith("Access denied");
        assertThat(String.join("\n", lines)).doesNotContain(TestTokens.OPS_ONLY, TestTokens.FULL, TestTokens.ADMIN_ONLY);
    }

    @Test
    void noTokenCannotEvenInitialize() {
        assertThatThrownBy(() -> connect("wrong-token")).isInstanceOf(RuntimeException.class);
        client = null;
    }
}
