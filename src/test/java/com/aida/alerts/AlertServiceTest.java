package com.aida.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.aida.mock.MockStore;
import com.aida.security.TestTokens;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** The alert run against the mock data with the real engines, its state and webhook, and the admin endpoint. */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18083",
        "aida.jira.base-url=http://localhost:18083/mock/jira",
        "aida.leave.base-url=http://localhost:18083/mock",
        "aida.data-dir=target/test-data/alerts"})
@Import(DemoClock.class)
class AlertServiceTest {

    static final List<String> webhookBodies = new CopyOnWriteArrayList<>();
    static final HttpServer webhook = startWebhook();

    static HttpServer startWebhook() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/hook", exchange -> {
                webhookBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void webhookUrl(DynamicPropertyRegistry registry) {
        registry.add("aida.alerts.webhook", () -> "http://localhost:" + webhook.getAddress().getPort() + "/hook");
    }

    @AfterAll
    static void stopWebhook() {
        webhook.stop(0);
    }

    @Autowired
    AlertService alerts;

    @Autowired
    MockStore store;

    @Autowired
    JsonMapper mapper;

    @Value("${aida.data-dir}")
    Path dataDir;

    @BeforeEach
    void setUp() throws IOException {
        store.reset();
        alerts.clearState();
        Files.deleteIfExists(dataDir.resolve("alerts.log"));
        webhookBodies.clear();
    }

    @Test
    void firstRunFlagsTheShop32ConfidenceDropAgainstLastWeek() throws IOException {
        AlertService.RunResult result = alerts.run();

        assertThat(result.asOf()).isEqualTo(DemoClock.NOW.toLocalDate());
        assertThat(result.checked()).containsExactly("SHOP/3.2", "SHOP/3.3");
        assertThat(result.skipped()).singleElement().asString().startsWith("OPS: data quality");
        assertThat(result.alerts()).filteredOn(a -> a.release().equals("3.2")).singleElement().satisfies(a -> {
            assertThat(a.reasons()).isNotEmpty();
            assertThat(a.before().asOf()).isEqualTo(result.asOf().minusDays(7));
            assertThat(a.message()).startsWith("SHOP 3.2: ");
        });

        assertThat(Files.readAllLines(dataDir.resolve("alerts.log"))).hasSize(result.alerts().size());
        assertThat(Files.readString(dataDir.resolve("alert-state.json"))).contains("SHOP/3.2", "SHOP/3.3");
        assertThat(webhookBodies).singleElement().asString().contains("\"alerts\"", "SHOP 3.2");
    }

    @Test
    void secondRunComparesWithTheSavedStateAndStaysQuiet() {
        alerts.run();
        webhookBodies.clear();

        AlertService.RunResult again = alerts.run();
        assertThat(again.alerts()).isEmpty();
        assertThat(webhookBodies).isEmpty();
    }

    @Test
    void addingLeaveAfterTheLastRunIsComparedWithThatRun() {
        alerts.run();
        store.addLeave(new com.aida.contract.Person("Arjun", "IN"), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 6));
        AlertService.RunResult afterLeave = alerts.run();
        // Whether 5 days of leave crosses a threshold is data tuning (S4); the comparison base is the last run.
        afterLeave.alerts().forEach(a -> assertThat(a.before().asOf()).isEqualTo(afterLeave.asOf()));
    }

    @Test
    void adminEndpointNeedsAdminScope() {
        RestClient http = RestClient.create("http://localhost:18083");
        assertThat(status(http, null)).isEqualTo(401);
        assertThat(status(http, TestTokens.OPS_ONLY)).isEqualTo(403);
        assertThat(status(http, TestTokens.FULL)).isEqualTo(200);

        String body = http.post().uri("/admin/alerts/run").header("Authorization", "Bearer " + TestTokens.FULL)
                .retrieve().body(String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(body, Map.class);
        assertThat(json).containsKeys("ranAt", "asOf", "checked", "skipped", "alerts");
    }

    static int status(RestClient http, String token) {
        return http.post().uri("/admin/alerts/run")
                .headers(h -> {
                    if (token != null) {
                        h.setBearerAuth(token);
                    }
                })
                .exchange((request, response) -> response.getStatusCode()).value();
    }

    @Test
    void thresholds() {
        LocalDate d = LocalDate.of(2026, 10, 27);
        AlertService.Snapshot base = new AlertService.Snapshot(d.minusDays(7), 0.60, d.plusDays(20));

        assertThat(AlertService.compare("SHOP", "3.2", d, base, new AlertService.Snapshot(d, 0.45, d.plusDays(20))))
                .hasValueSatisfying(a -> assertThat(a.reasons()).containsExactly("confidence_drop"));
        assertThat(AlertService.compare("SHOP", "3.2", d, base, new AlertService.Snapshot(d, 0.46, d.plusDays(24))))
                .isEmpty();
        assertThat(AlertService.compare("SHOP", "3.2", d, base, new AlertService.Snapshot(d, 0.80, d.plusDays(25))))
                .hasValueSatisfying(a -> assertThat(a.reasons()).containsExactly("p85_later"));
        assertThat(AlertService.compare("SHOP", "3.2", d, base, new AlertService.Snapshot(d, 0.60, d.plusDays(15))))
                .hasValueSatisfying(a -> assertThat(a.reasons()).containsExactly("p85_earlier"));
        assertThat(AlertService.compare("SHOP", "3.2", d, base, new AlertService.Snapshot(d, null, d.plusDays(20))))
                .isEmpty();
        assertThat(AlertService.compare("SHOP", "3.2", d, base, new AlertService.Snapshot(d, 0.30, d.plusDays(30))))
                .hasValueSatisfying(a -> {
                    assertThat(a.reasons()).containsExactly("confidence_drop", "p85_later");
                    assertThat(a.message()).isEqualTo("SHOP 3.2: chance of making the target fell from 60% to 30%; "
                            + "p85 moved 10 days later, from 2026-11-16 to 2026-11-26 (compared with 2026-10-20).");
                });
    }
}
