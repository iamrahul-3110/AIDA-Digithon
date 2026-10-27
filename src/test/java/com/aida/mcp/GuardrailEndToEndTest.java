package com.aida.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.aida.security.TestTokens;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.json.JsonMapper;

/** verify_answer over real MCP, against a real forecast_delivery result (real engine, mock data over HTTP). */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18082",
        "aida.jira.base-url=http://localhost:18082/mock/jira",
        "aida.leave.base-url=http://localhost:18082/mock"})
class GuardrailEndToEndTest {

    static final String AS_OF = "2026-10-27";
    static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    @Autowired
    JsonMapper mapper;

    final List<McpSyncClient> clients = new java.util.ArrayList<>();

    McpSyncClient connect(String token) {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:18082")
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build()).requestTimeout(Duration.ofSeconds(60)).build();
        client.initialize();
        clients.add(client);
        return client;
    }

    @AfterEach
    void close() {
        clients.forEach(McpSyncClient::closeGracefully);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> json(CallToolResult result) {
        assertThat(result.isError()).as(((TextContent) result.content().getFirst()).text()).isNotEqualTo(Boolean.TRUE);
        return mapper.readValue(((TextContent) result.content().getFirst()).text(), Map.class);
    }

    Map<String, Object> verify(McpSyncClient client, String draft) {
        return json(client.callTool(new CallToolRequest(Guardrail.TOOL, Map.of("draft", draft))));
    }

    /** A draft the way Claude writes one, from the real result. */
    String draftFrom(Map<String, Object> forecast) {
        long percent = Math.round(((Number) forecast.get("pByTarget")).doubleValue() * 100);
        return "SHOP 3.2 has a " + percent + "% chance of shipping by "
                + LocalDate.parse((String) forecast.get("targetDate")).format(MONTH_DAY) + ". "
                + forecast.get("remainingItems") + " items remain; p85 is "
                + LocalDate.parse((String) forecast.get("p85")).format(MONTH_DAY) + " and p95 is "
                + forecast.get("p95") + ", from " + forecast.get("sampleWeeks") + " weeks of throughput.";
    }

    @Test
    void draftFromARealForecastPassesAndAnInventedPercentageIsFlagged() {
        McpSyncClient lana = connect(TestTokens.FULL);
        Map<String, Object> forecast = json(lana.callTool(new CallToolRequest("forecast_delivery",
                Map.of("project", "SHOP", "release", "3.2", "asOf", AS_OF))));
        String draft = draftFrom(forecast);

        Map<String, Object> ok = verify(lana, draft);
        assertThat(ok).as(draft).containsEntry("ok", true).containsEntry("unmatched", List.of());
        assertThat(ok).containsKey("meta");

        long real = Math.round(((Number) forecast.get("pByTarget")).doubleValue() * 100);
        long invented = real >= 90 ? real - 20 : real + 20;
        Map<String, Object> bad = verify(lana, draft.replace(real + "%", invented + "%"));
        assertThat(bad).containsEntry("ok", false).containsEntry("unmatched", List.of(invented + "%"));

        // The guardrail's own output never becomes a source: the invented value stays flagged.
        assertThat(verify(lana, "Chance: " + invented + "%.")).containsEntry("ok", false);
    }

    @Test
    void anotherTokensResultsDoNotCount() {
        McpSyncClient lana = connect(TestTokens.FULL);
        Map<String, Object> forecast = json(lana.callTool(new CallToolRequest("forecast_delivery",
                Map.of("project", "SHOP", "release", "3.2", "asOf", AS_OF))));
        String draft = draftFrom(forecast);

        Map<String, Object> other = verify(connect(TestTokens.OPS_ONLY), draft);
        assertThat(other).containsEntry("ok", false);
        assertThat((List<?>) other.get("unmatched")).isNotEmpty();
    }

    @Test
    void verifyAnswerIsListed() {
        assertThat(connect(TestTokens.FULL).listTools().tools()).extracting(t -> t.name()).contains(Guardrail.TOOL);
    }
}
