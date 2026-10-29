package com.aida.alerts;

import com.aida.adapter.JiraSource;
import com.aida.adapter.LeaveSource;
import com.aida.contract.*;
import com.aida.engine.ForecastEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final JiraSource jiraSource;
    private final LeaveSource leaveSource;
    private final ObjectMapper objectMapper;

    @Value("${aida.data-dir:./data}")
    private String dataDir;

    @Value("${aida.alerts.webhook:}")
    private String webhookUrl;

    private final RestClient restClient;

    public AlertService(JiraSource jiraSource, LeaveSource leaveSource,
                        ObjectMapper objectMapper, RestClient.Builder builder) {
        this.jiraSource = jiraSource;
        this.leaveSource = leaveSource;
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    @Scheduled(cron = "${aida.alerts.cron:0 0 2 * * *}")
    public void scheduledRun() {
        log.info("Running scheduled alert check");
        runAlerts();
    }

    public void runAlerts() {
        String[] projects = {"SHOP", "OPS"};
        Map<String, Object> prevState = loadState();
        Map<String, Object> newState = new HashMap<>();

        for (String project : projects) {
            try {
                List<Release> releases = jiraSource.releases(project);
                for (Release release : releases) {
                    if (release.released()) continue;
                    String key = project + ":" + release.name();
                    LocalDate asOf = LocalDate.now();
                    List<Issue> issues = jiraSource.issues(project, release.name());
                    List<Person> team = leaveSource.team(project);
                    List<Absence> absences = leaveSource.absences(asOf.minusMonths(1), asOf.plusMonths(6));
                    Forecast forecast = ForecastEngine.forecast(issues, release.name(), asOf, team, absences,
                        release.releaseDate(), 10000, 42);

                    double currentP = forecast.pByTarget() != null ? forecast.pByTarget() : 0;
                    int currentP85Days = forecast.p85() != null
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), forecast.p85()) : 0;

                    newState.put(key + ":pByTarget", currentP);
                    newState.put(key + ":p85Days", currentP85Days);

                    // Compare with previous
                    Double prevP = (Double) prevState.getOrDefault(key + ":pByTarget", null);
                    Integer prevP85 = prevState.get(key + ":p85Days") instanceof Number n ? n.intValue() : null;

                    List<String> alerts = new ArrayList<>();
                    if (prevP != null && (prevP - currentP) >= 0.15) {
                        alerts.add(String.format("pByTarget dropped from %.0f%% to %.0f%%", prevP * 100, currentP * 100));
                    }
                    if (prevP85 != null && (currentP85Days - prevP85) >= 5) {
                        alerts.add(String.format("p85 moved %d days later", currentP85Days - prevP85));
                    }

                    for (String alert : alerts) {
                        String msg = String.format("[AIDA ALERT] %s %s: %s", project, release.name(), alert);
                        log.warn(msg);
                        appendAlert(msg, project, release.name(), alert);
                        postWebhook(msg);
                    }
                }
            } catch (Exception e) {
                log.error("Alert check failed for {}: {}", project, e.getMessage());
            }
        }
        saveState(newState);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadState() {
        Path path = Path.of(dataDir, "alert-state.json");
        if (!Files.exists(path)) return new HashMap<>();
        try {
            return objectMapper.readValue(path.toFile(), HashMap.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveState(Map<String, Object> state) {
        try {
            Files.createDirectories(Path.of(dataDir));
            objectMapper.writeValue(Path.of(dataDir, "alert-state.json").toFile(), state);
        } catch (IOException e) {
            log.error("Failed to save alert state: {}", e.getMessage());
        }
    }

    private void appendAlert(String msg, String project, String release, String detail) {
        try {
            Files.createDirectories(Path.of(dataDir));
            try (PrintWriter pw = new PrintWriter(new FileWriter(Path.of(dataDir, "alerts.log").toFile(), true))) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("ts", java.time.Instant.now().toString());
                entry.put("project", project);
                entry.put("release", release);
                entry.put("alert", detail);
                pw.println(objectMapper.writeValueAsString(entry));
            }
        } catch (IOException e) {
            log.error("Failed to write alerts log: {}", e.getMessage());
        }
    }

    private void postWebhook(String msg) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        try {
            restClient.post().uri(webhookUrl)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("text", msg))
                .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("Webhook post failed: {}", e.getMessage());
        }
    }
}
