package com.aida.alerts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.aida.contract.DataQuality;
import com.aida.contract.Forecast;
import com.aida.contract.Release;
import com.aida.engine.ForecastEngine;
import com.aida.engine.QualityEngine;
import com.aida.mcp.ProjectData;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks every unreleased release and alerts when its forecast got worse: pByTarget dropped 15+ points or p85 moved
 * 5+ days. Compares with the last run's forecast in {@code data/alert-state.json}; a release with no saved state is
 * compared with its forecast as of 7 days earlier, so the first run already reports last week's change. Projects
 * whose data quality does not allow a forecast are skipped. Every alert is logged and appended to
 * {@code data/alerts.log}, and POSTed to {@code aida.alerts.webhook} when set. Runs on {@code aida.alerts.cron} and
 * on demand through {@code POST /admin/alerts/run}.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    static final double PBYTARGET_DROP = 0.15;
    static final int P85_MOVE_DAYS = 5;
    static final int BASELINE_DAYS = 7;

    /** What was last seen for one release. */
    public record Snapshot(LocalDate asOf, Double pByTarget, LocalDate p85) {
    }

    /** One alert: before and after values and a plain sentence. */
    public record Alert(String project, String release, LocalDate asOf, List<String> reasons, Snapshot before,
            Snapshot now, String message) {
    }

    /** The outcome of one run. */
    public record RunResult(Instant ranAt, LocalDate asOf, List<String> checked, List<String> skipped,
            List<Alert> alerts) {
    }

    private final ProjectData projectData;
    private final ForecastEngine forecastEngine;
    private final QualityEngine qualityEngine;
    private final JsonMapper mapper;
    private final Clock clock;
    private final List<String> projects;
    private final String webhook;
    private final Path stateFile;
    private final Path alertsFile;

    public AlertService(ProjectData projectData, ForecastEngine forecastEngine, QualityEngine qualityEngine,
            JsonMapper mapper, Clock clock, @Value("${aida.alerts.projects:SHOP,OPS}") List<String> projects,
            @Value("${aida.alerts.webhook:}") String webhook, @Value("${aida.data-dir}") Path dataDir) {
        this.projectData = projectData;
        this.forecastEngine = forecastEngine;
        this.qualityEngine = qualityEngine;
        this.mapper = mapper;
        this.clock = clock;
        this.projects = projects.stream().map(p -> p.trim().toUpperCase()).toList();
        this.webhook = webhook == null ? "" : webhook.trim();
        this.stateFile = dataDir.resolve("alert-state.json");
        this.alertsFile = dataDir.resolve("alerts.log");
    }

    @Scheduled(cron = "${aida.alerts.cron}")
    public void scheduled() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Scheduled alert run failed", e);
        }
    }

    public synchronized RunResult run() {
        LocalDate asOf = LocalDate.now(clock);
        Map<String, Snapshot> state = readState();
        List<String> checked = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();

        for (String project : projects) {
            ProjectData.Data data = projectData.load(project, asOf);
            DataQuality quality = qualityEngine.assess(data.issues(), asOf);
            if (!quality.forecastAllowed()) {
                skipped.add(project + ": data quality " + quality.score() + "/100 does not allow a forecast");
                continue;
            }
            for (Release release : data.releases()) {
                if (release.released()) {
                    continue;
                }
                String key = project + "/" + release.name();
                Snapshot now = snapshot(forecast(data, release, asOf));
                Snapshot before = state.get(key);
                if (before == null) {
                    before = snapshot(forecast(data, release, asOf.minusDays(BASELINE_DAYS)));
                }
                checked.add(key);
                compare(project, release.name(), asOf, before, now).ifPresent(alerts::add);
                state.put(key, now);
            }
        }

        writeState(state);
        RunResult result = new RunResult(clock.instant(), asOf, checked, skipped, alerts);
        for (Alert alert : alerts) {
            log.warn("AIDA alert: {}", alert.message());
            append(alert);
        }
        if (!alerts.isEmpty() && !webhook.isEmpty()) {
            post(result);
        }
        return result;
    }

    /** Forgets every saved forecast, e.g. after the demo data is reset. */
    public synchronized void clearState() {
        try {
            Files.deleteIfExists(stateFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Optional<Alert> compare(String project, String release, LocalDate asOf, Snapshot before,
            Snapshot now) {
        List<String> reasons = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        if (before.pByTarget() != null && now.pByTarget() != null
                && before.pByTarget() - now.pByTarget() >= PBYTARGET_DROP - 1e-9) {
            reasons.add("confidence_drop");
            parts.add(String.format("chance of making the target fell from %d%% to %d%%",
                    Math.round(before.pByTarget() * 100), Math.round(now.pByTarget() * 100)));
        }
        if (before.p85() != null && now.p85() != null) {
            long moved = ChronoUnit.DAYS.between(before.p85(), now.p85());
            if (Math.abs(moved) >= P85_MOVE_DAYS) {
                reasons.add(moved > 0 ? "p85_later" : "p85_earlier");
                parts.add(String.format("p85 moved %d days %s, from %s to %s", Math.abs(moved),
                        moved > 0 ? "later" : "earlier", before.p85(), now.p85()));
            }
        }
        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        String message = project + " " + release + ": " + String.join("; ", parts) + " (compared with "
                + before.asOf() + ").";
        return Optional.of(new Alert(project, release, asOf, List.copyOf(reasons), before, now, message));
    }

    private Forecast forecast(ProjectData.Data data, Release release, LocalDate asOf) {
        return forecastEngine.forecast(data.issues(), release.name(), asOf, data.team(), data.absences(),
                release.releaseDate());
    }

    private static Snapshot snapshot(Forecast f) {
        return new Snapshot(f.asOf(), f.pByTarget(), f.p85());
    }

    private Map<String, Snapshot> readState() {
        if (!Files.exists(stateFile)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(mapper.readValue(Files.readString(stateFile),
                    new TypeReference<Map<String, Snapshot>>() {
                    }));
        } catch (IOException | RuntimeException e) {
            log.warn("Ignoring unreadable {}: {}", stateFile, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void writeState(Map<String, Snapshot> state) {
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void append(Alert alert) {
        try {
            Files.createDirectories(alertsFile.getParent());
            Files.writeString(alertsFile, mapper.writeValueAsString(alert) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sends {"text": ..., "alerts": [...]}; a failing webhook is logged and never fails the run. */
    private void post(RunResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", String.join("\n", result.alerts().stream().map(Alert::message).toList()));
        body.put("alerts", result.alerts());
        try {
            RestClient.create().post().uri(webhook).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Alert webhook failed: {}", e.getMessage());
        }
    }
}
