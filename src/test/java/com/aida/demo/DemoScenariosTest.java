package com.aida.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.aida.alerts.DemoClock;
import com.aida.mock.DataGenerator;
import com.aida.security.TestTokens;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.json.JsonMapper;

/**
 * The six demo scenarios from the spec, end to end: MCP client with a bearer token -> /mcp -> tools -> HTTP adapters
 * -> mock, with the real engines, after a reset, on the demo date.
 *
 * <p>Tagged "demo": excluded from the default build until SYNC S4 tunes the data so every scenario is in range. Run
 * with {@code ./mvnw verify -Pdemo}.
 */
@Tag("demo")
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18084",
        "aida.jira.base-url=http://localhost:18084/mock/jira",
        "aida.leave.base-url=http://localhost:18084/mock",
        "aida.data-dir=target/test-data/demo"})
@Import(DemoClock.class)
class DemoScenariosTest {

    static final String BASE = "http://localhost:18084";
    static final LocalDate TODAY = DemoClock.NOW.toLocalDate();

    @Autowired
    JsonMapper mapper;

    final RestClient http = RestClient.create(BASE);
    final List<McpSyncClient> clients = new ArrayList<>();
    McpSyncClient claude;

    @BeforeEach
    void reset() {
        http.post().uri("/mock/admin/reset").retrieve().toBodilessEntity();
        claude = connect(TestTokens.FULL);
    }

    @AfterEach
    void close() {
        clients.forEach(McpSyncClient::closeGracefully);
    }

    McpSyncClient connect(String token) {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder(BASE).endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build()).requestTimeout(Duration.ofSeconds(60)).build();
        client.initialize();
        clients.add(client);
        return client;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> call(String tool, Map<String, Object> args) {
        CallToolResult result = claude.callTool(new CallToolRequest(tool, args));
        String text = ((TextContent) result.content().getFirst()).text();
        assertThat(result.isError()).as(tool + ": " + text).isNotEqualTo(Boolean.TRUE);
        return mapper.readValue(text, Map.class);
    }

    Map<String, Object> forecastShop32() {
        return call("forecast_delivery", Map.of("project", "SHOP", "release", "3.2"));
    }

    static double number(Map<String, Object> json, String key) {
        return ((Number) json.get(key)).doubleValue();
    }

    static LocalDate date(Map<String, Object> json, String key) {
        return LocalDate.parse((String) json.get(key));
    }

    void addLeave(String person, String start, String end) {
        http.post().uri("/mock/calendar/events").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("person", person, "start", start, "end", end)).retrieve().toBodilessEntity();
    }

    @Test
    @DisplayName("1. Will SHOP 3.2 ship by its target date? pByTarget 0.35-0.55, p85 7-14 days after target")
    void scenario1() {
        Map<String, Object> f = forecastShop32();
        assertThat(number(f, "pByTarget")).as("pByTarget").isBetween(0.35, 0.55);
        assertThat(ChronoUnit.DAYS.between(date(f, "targetDate"), date(f, "p85"))).as("p85 - target, days")
                .isBetween(7L, 14L);
    }

    @Test
    @DisplayName("2. 5 days of leave for Arjun moves p85 later or equal and explain_change lists a leave driver")
    @SuppressWarnings("unchecked")
    void scenario2() {
        LocalDate p85Before = date(forecastShop32(), "p85");
        LocalDate monday = TODAY.plusDays(7 - TODAY.getDayOfWeek().getValue() + 1);
        addLeave("Arjun", monday.toString(), monday.plusDays(4).toString());

        Map<String, Object> after = forecastShop32();
        assertThat(date(after, "p85")).as("p85 after leave").isAfterOrEqualTo(p85Before);

        Map<String, Object> explanation = call("explain_change", Map.of("project", "SHOP", "release", "3.2",
                "fromDate", TODAY.minusDays(7).toString(), "toDate", TODAY.toString()));
        assertThat((List<Map<String, Object>>) explanation.get("drivers")).extracting(d -> d.get("type"))
                .contains("leave");
    }

    @Test
    @DisplayName("3. What if we drop the Gift cards epic? pByTarget 0.70-0.90")
    void scenario3() {
        Map<String, Object> whatIf = call("run_what_if", Map.of("project", "SHOP", "release", "3.2",
                "dropEpics", List.of("Gift cards")));
        assertThat(number(whatIf, "pByTarget")).as("pByTarget without Gift cards").isBetween(0.70, 0.90);
        assertThat(number(whatIf, "pByTarget")).isGreaterThanOrEqualTo(number(forecastShop32(), "pByTarget"));
    }

    @Test
    @DisplayName("4. OPS: score below 60, forecastAllowed false, stale items listed")
    @SuppressWarnings("unchecked")
    void scenario4() {
        Map<String, Object> quality = call("check_data_quality", Map.of("project", "OPS"));
        assertThat(number(quality, "score")).isLessThan(60);
        assertThat(quality).containsEntry("forecastAllowed", false);
        assertThat((List<Map<String, Object>>) quality.get("issues"))
                .filteredOn(i -> "stale_status".equals(i.get("type")))
                .singleElement().satisfies(i -> assertThat((List<?>) i.get("keys")).isNotEmpty());
    }

    @Test
    @DisplayName("5. Weekly status for team, management, client and HQ; the client report names no team member")
    void scenario5() {
        assumeTrue(claude.listTools().tools().stream().anyMatch(t -> t.name().equals("build_report")),
                "build_report (Person B, B5) is not on main yet");
        for (String audience : List.of("team", "management", "client", "hq")) {
            Map<String, Object> report = call("build_report", Map.of("project", "SHOP", "release", "3.2",
                    "audience", audience));
            assertThat(report).as(audience).containsEntry("audience", audience);
            if (audience.equals("client")) {
                String json = mapper.writeValueAsString(report);
                List.of(DataGenerator.SHOP_TEAM, DataGenerator.OPS_TEAM).forEach(team -> team.forEach(
                        p -> assertThat(json).as("client report").doesNotContain(p.name())));
            }
        }
    }

    @Test
    @DisplayName("6. The alert run flags the SHOP 3.2 confidence drop")
    @SuppressWarnings("unchecked")
    void scenario6() {
        String body = http.post().uri("/admin/alerts/run").header("Authorization", "Bearer " + TestTokens.FULL)
                .retrieve().body(String.class);
        Map<String, Object> run = mapper.readValue(body, Map.class);
        assertThat((List<Map<String, Object>>) run.get("alerts"))
                .filteredOn(a -> "SHOP".equals(a.get("project")) && "3.2".equals(a.get("release")))
                .singleElement()
                .satisfies(a -> assertThat((List<String>) a.get("reasons")).contains("confidence_drop"));
    }
}
