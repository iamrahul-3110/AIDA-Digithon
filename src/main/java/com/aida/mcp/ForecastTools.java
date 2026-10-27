package com.aida.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.aida.contract.Release;
import com.aida.engine.ForecastEngine;
import com.aida.engine.QualityEngine;

/** forecast_delivery and check_data_quality. */
@Component
public class ForecastTools implements AidaToolSet {

    private final ToolSupport support;
    private final ProjectData projectData;
    private final ForecastEngine forecastEngine;
    private final QualityEngine qualityEngine;
    private final Clock clock;

    public ForecastTools(ToolSupport support, ProjectData projectData, ForecastEngine forecastEngine,
            QualityEngine qualityEngine, Clock clock) {
        this.support = support;
        this.projectData = projectData;
        this.forecastEngine = forecastEngine;
        this.qualityEngine = qualityEngine;
        this.clock = clock;
    }

    @Tool(name = "forecast_delivery", description = """
            Forecast when a release will be done, from a Monte Carlo simulation over the team's recent throughput, \
            adjusted for planned leave and public holidays. Returns p50/p85/p95 finish dates, the probability of \
            finishing by the target date (pByTarget, 0..1), remaining items, the data window and sample size. \
            Every number comes from AIDA's engine: quote them, do not estimate your own.""")
    public Map<String, Object> forecastDelivery(
            @ToolParam(description = "Project key, e.g. SHOP or OPS") String project,
            @ToolParam(description = "Release (Jira fixVersion) name, e.g. 3.2") String release,
            @ToolParam(required = false, description = "Forecast as of this date, yyyy-MM-dd; defaults to today") String asOf,
            @ToolParam(required = false, description = "Target date, yyyy-MM-dd; defaults to the release's planned date") String targetDate) {
        Map<String, Object> args = args("project", project, "release", release, "asOf", asOf, "targetDate", targetDate);
        return support.call("forecast_delivery", project, args, () -> {
            String key = project.toUpperCase();
            LocalDate date = asOf == null || asOf.isBlank() ? LocalDate.now(clock) : date("asOf", asOf);
            ProjectData.Data data = projectData.load(key, date);
            Release rel = data.releases().stream().filter(r -> r.name().equals(release)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown release '" + release + "' in " + key
                            + "; known releases: " + data.releases().stream().map(Release::name).toList()));
            LocalDate target = targetDate == null || targetDate.isBlank() ? rel.releaseDate() : date("targetDate", targetDate);
            return forecastEngine.forecast(data.issues(), rel.name(), date, data.team(), data.absences(), target);
        });
    }

    @Tool(name = "check_data_quality", description = """
            Check whether a project's Jira data is good enough to forecast: stale statuses, stories without \
            estimates and bulk closes, a 0..100 score, and forecastAllowed. When forecastAllowed is false, explain \
            the listed problems instead of giving a delivery date.""")
    public Map<String, Object> checkDataQuality(
            @ToolParam(description = "Project key, e.g. SHOP or OPS") String project) {
        return support.call("check_data_quality", project, args("project", project), () -> {
            LocalDate today = LocalDate.now(clock);
            return qualityEngine.assess(projectData.load(project.toUpperCase(), today).issues(), today);
        });
    }

    private static LocalDate date(String name, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a date like 2026-12-15, got '" + value + "'");
        }
    }

    /** Non-null arguments in order, for the audit log. */
    private static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                args.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return args;
    }
}
