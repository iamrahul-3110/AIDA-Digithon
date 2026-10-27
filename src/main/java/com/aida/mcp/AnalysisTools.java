package com.aida.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
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

    private final ToolSupport toolSupport;
    private final ProjectData projectData;
    private final ExplainEngine explainEngine;
    private final WhatIfEngine whatIfEngine;
    private final RiskEngine riskEngine;
    private final JsonMapper mapper;
    private final Clock clock;

    public AnalysisTools(ToolSupport toolSupport, ProjectData projectData, ExplainEngine explainEngine,
            WhatIfEngine whatIfEngine, RiskEngine riskEngine, JsonMapper mapper, ObjectProvider<Clock> clock) {
        this.toolSupport = toolSupport;
        this.projectData = projectData;
        this.explainEngine = explainEngine;
        this.whatIfEngine = whatIfEngine;
        this.riskEngine = riskEngine;
        this.mapper = mapper;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
    }

    @Tool(name = "explain_change", description = """
            Explains why a release's p85 delivery date moved between two dates. Forecasts the release as it stood \
            at fromDate and at toDate and splits the shift in days (shiftDays) into drivers: scope_added, \
            client_wait, leave, aging and throughput_drop. Each driver has impactDays (they sum exactly to \
            shiftDays), one plain sentence with numbers, and the issue or absence keys behind it.""")
    public Map<String, Object> explainChange(
            @ToolParam(description = "Jira project key, e.g. SHOP") String project,
            @ToolParam(description = "Release (fixVersion) name, e.g. 3.2") String release,
            @ToolParam(description = "Earlier date to compare from, YYYY-MM-DD (e.g. 7 days ago)") String fromDate,
            @ToolParam(description = "Later date to compare to, YYYY-MM-DD (usually today)") String toDate) {
        Map<String, Object> args = args("project", project, "release", release, "fromDate", fromDate,
                "toDate", toDate);
        return toolSupport.call("explain_change", project, args, () -> {
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            if (!from.isBefore(to)) {
                throw new IllegalArgumentException("fromDate must be before toDate: " + from + " / " + to);
            }
            ProjectData.Data data = projectData.load(project, to);
            requireRelease(data, project, release);
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
            @ToolParam(description = "Jira project key, e.g. SHOP") String project,
            @ToolParam(description = "Release (fixVersion) name, e.g. 3.2") String release,
            @ToolParam(description = "Epics to drop, by key (SHOP-E7) or name (Gift cards)", required = false)
            List<String> dropEpics,
            @ToolParam(description = "People to add, each with name and country (IN, KR or VN)", required = false)
            List<Person> addPeople,
            @ToolParam(description = "Names of team members to remove", required = false) List<String> removePeople,
            @ToolParam(description = "Target date YYYY-MM-DD; defaults to the release date", required = false)
            String targetDate) {
        Map<String, Object> args = args("project", project, "release", release, "dropEpics", dropEpics,
                "addPeople", addPeople, "removePeople", removePeople, "targetDate", targetDate);
        return toolSupport.call("run_what_if", project, args, () -> {
            LocalDate asOf = LocalDate.now(clock);
            ProjectData.Data data = projectData.load(project, asOf);
            Release rel = requireRelease(data, project, release);
            LocalDate target = targetDate == null || targetDate.isBlank() ? rel.releaseDate()
                    : LocalDate.parse(targetDate);
            return whatIfEngine.run(data.issues(), release, asOf, data.team(), data.absences(),
                    new WhatIf(dropEpics, addPeople, removePeople, target));
        });
    }

    @Tool(name = "get_risks", description = """
            Lists the project's at-risk active items today with days in their current status: client_wait \
            (waiting for client UAT 5+ days, side client), stale (no status change for 14+ days, side team) and \
            aging (over twice the median time for their status, side team), longest first, plus the bottleneck \
            stage (the active status holding the most item-days).""")
    public Map<String, Object> getRisks(@ToolParam(description = "Jira project key, e.g. SHOP") String project) {
        return toolSupport.call("get_risks", project, args("project", project), () -> {
            LocalDate asOf = LocalDate.now(clock);
            RiskReport report = riskEngine.assess(projectData.load(project, asOf).issues(), asOf);
            return withWindow(report, asOf, asOf);
        });
    }

    private static Release requireRelease(ProjectData.Data data, String project, String release) {
        return data.releases().stream().filter(r -> r.name().equals(release)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown release " + release + " in project "
                        + project + "; known releases: " + data.releases().stream().map(Release::name).toList()));
    }

    /** The result as a map with the data window ToolSupport copies into the meta block. */
    private Map<String, Object> withWindow(Object result, LocalDate windowStart, LocalDate windowEnd) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = new LinkedHashMap<>(mapper.convertValue(result, Map.class));
        map.put("windowStart", windowStart.toString());
        map.put("windowEnd", windowEnd.toString());
        return map;
    }

    /** Argument map for the audit line; keeps nulls, unlike Map.of. */
    static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            args.put((String) keyValues[i], keyValues[i + 1]);
        }
        return args;
    }
}
