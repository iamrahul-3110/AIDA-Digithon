package com.aida.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.aida.contract.ChangeExplanation;
import com.aida.contract.Person;
import com.aida.contract.Release;
import com.aida.contract.RiskReport;
import com.aida.contract.WhatIf;
import com.aida.engine.ExplainEngine;
import com.aida.engine.ForecastEngine;
import com.aida.engine.RiskEngine;
import com.aida.engine.WhatIfEngine;

import tools.jackson.databind.json.JsonMapper;

/** explain_change, run_what_if and get_risks. Every call goes through {@link ToolSupport#call}. */
@Component
public class AnalysisTools implements AidaToolSet {

    private final ToolSupport support;
    private final ProjectData projectData;
    private final ExplainEngine explainEngine;
    private final WhatIfEngine whatIfEngine;
    private final RiskEngine riskEngine;
    private final JsonMapper mapper;
    private final Clock clock;

    public AnalysisTools(ToolSupport support, ProjectData projectData, ExplainEngine explainEngine,
            WhatIfEngine whatIfEngine, RiskEngine riskEngine, JsonMapper mapper, Clock clock) {
        this.support = support;
        this.projectData = projectData;
        this.explainEngine = explainEngine;
        this.whatIfEngine = whatIfEngine;
        this.riskEngine = riskEngine;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Tool(name = "explain_change", description = """
            Explains why a release's p85 delivery date moved between two dates. Forecasts the release as it stood \
            at fromDate and at toDate and splits the shift in days (shiftDays) into drivers: scope_added, \
            client_wait, leave, aging and throughput_drop. Each driver has impactDays (they sum exactly to \
            shiftDays), one plain sentence with numbers, and the issue or absence keys behind it. \
            Every number comes from AIDA's engine: quote them, do not estimate your own.""")
    public Map<String, Object> explainChange(
            @ToolParam(description = "Project key, e.g. SHOP or OPS") String project,
            @ToolParam(description = "Release (Jira fixVersion) name, e.g. 3.2") String release,
            @ToolParam(description = "Earlier date to compare from, yyyy-MM-dd (e.g. 7 days ago)") String fromDate,
            @ToolParam(description = "Later date to compare to, yyyy-MM-dd (usually today)") String toDate) {
        Map<String, Object> args = args("project", project, "release", release, "fromDate", fromDate,
                "toDate", toDate);
        return support.call("explain_change", project, args, () -> {
            LocalDate from = date("fromDate", fromDate);
            LocalDate to = date("toDate", toDate);
            if (!from.isBefore(to)) {
                throw new IllegalArgumentException("fromDate must be before toDate, got " + from + " and " + to);
            }
            String key = project.toUpperCase();
            ProjectData.Data data = projectData.load(key, to);
            requireRelease(data, key, release);
            ChangeExplanation explanation = explainEngine.explain(data.issues(), release, from, to, data.team(),
                    data.absences());
            return withWindow(explanation, ForecastEngine.windowStart(from), ForecastEngine.windowEnd(to));
        });
    }

    @Tool(name = "run_what_if", description = """
            Forecasts a release under a changed scope or team, to compare with forecast_delivery: drop epics \
            (their not-Done items leave the scope), add people or remove people (throughput scales with team \
            size, capacity is recomputed for the new team). Returns the same Forecast shape: p50/p85/p95 dates, \
            pByTarget (share of 10,000 simulation runs finished by the target date), data window and sample size.""")
    public Map<String, Object> runWhatIf(
            @ToolParam(description = "Project key, e.g. SHOP or OPS") String project,
            @ToolParam(description = "Release (Jira fixVersion) name, e.g. 3.2") String release,
            @ToolParam(required = false, description = "Epics to drop, by key (SHOP-E7) or name (Gift cards)")
            List<String> dropEpics,
            @ToolParam(required = false, description = "People to add, each with name and country (IN, KR or VN)")
            List<Person> addPeople,
            @ToolParam(required = false, description = "Names of team members to remove") List<String> removePeople,
            @ToolParam(required = false, description = "Target date, yyyy-MM-dd; defaults to the release's planned date")
            String targetDate) {
        Map<String, Object> args = args("project", project, "release", release, "dropEpics", dropEpics,
                "addPeople", addPeople, "removePeople", removePeople, "targetDate", targetDate);
        return support.call("run_what_if", project, args, () -> {
            String key = project.toUpperCase();
            LocalDate asOf = LocalDate.now(clock);
            ProjectData.Data data = projectData.load(key, asOf);
            Release rel = requireRelease(data, key, release);
            LocalDate target = targetDate == null || targetDate.isBlank() ? rel.releaseDate()
                    : date("targetDate", targetDate);
            return whatIfEngine.run(data.issues(), rel.name(), asOf, data.team(), data.absences(),
                    new WhatIf(dropEpics, addPeople, removePeople, target));
        });
    }

    @Tool(name = "get_risks", description = """
            Lists the project's at-risk active items today with days in their current status: client_wait \
            (waiting for client UAT 5+ days, side client), stale (no status change for 14+ days, side team) and \
            aging (over twice the median time for their status, side team), longest first, plus the bottleneck \
            stage (the active status holding the most item-days).""")
    public Map<String, Object> getRisks(@ToolParam(description = "Project key, e.g. SHOP or OPS") String project) {
        return support.call("get_risks", project, args("project", project), () -> {
            LocalDate asOf = LocalDate.now(clock);
            RiskReport report = riskEngine.assess(projectData.load(project.toUpperCase(), asOf).issues(), asOf);
            return withWindow(report, asOf, asOf);
        });
    }

    static Release requireRelease(ProjectData.Data data, String project, String release) {
        return data.releases().stream().filter(r -> r.name().equals(release)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown release '" + release + "' in " + project
                        + "; known releases: " + data.releases().stream().map(Release::name).toList()));
    }

    /** The result as a map with the data window ToolSupport copies into the meta block. */
    private Map<String, Object> withWindow(Object result, LocalDate windowStart, LocalDate windowEnd) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = new LinkedHashMap<>(mapper.convertValue(result, Map.class));
        map.put("windowStart", windowStart.toString());
        map.put("windowEnd", windowEnd.toString());
        return map;
    }

    static LocalDate date(String name, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(name + " must be a date like 2026-12-15, got '" + value + "'");
        }
    }

    /** Non-null arguments in order, for the audit log. */
    static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                args.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return args;
    }
}
