package com.aida.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.aida.contract.Forecast;
import com.aida.contract.Release;
import com.aida.engine.ForecastEngine;
import com.aida.engine.ReportEngine;

/** build_report. Every call goes through {@link ToolSupport#call}. */
@Component
public class ReportTools implements AidaToolSet {

    private final ToolSupport support;
    private final ProjectData projectData;
    private final ReportEngine reportEngine;
    private final ForecastEngine forecastEngine;
    private final Clock clock;

    public ReportTools(ToolSupport support, ProjectData projectData, ReportEngine reportEngine,
            ForecastEngine forecastEngine, Clock clock) {
        this.support = support;
        this.projectData = projectData;
        this.reportEngine = reportEngine;
        this.forecastEngine = forecastEngine;
        this.clock = clock;
    }

    @Tool(name = "build_report", description = """
            Builds the data for a weekly status report on a release for one audience: team, management, client \
            or hq. Contains the forecast against the release date, progress {done, remaining, total}, the top 3 \
            drivers of the last 7 days' date change, the top 5 risks and the items waiting on the client (asks). \
            hq gets forecast, progress and drivers only; client gets no team member names and no team-side risks. \
            Format it with the matching template of the aida-delivery-analyst skill.""")
    public Map<String, Object> buildReport(
            @ToolParam(description = "Project key, e.g. SHOP or OPS") String project,
            @ToolParam(description = "Release (Jira fixVersion) name, e.g. 3.2") String release,
            @ToolParam(description = "Audience: team, management, client or hq") String audience) {
        Map<String, Object> args = AnalysisTools.args("project", project, "release", release, "audience", audience);
        return support.call("build_report", project, args, () -> {
            String key = project.toUpperCase();
            LocalDate asOf = LocalDate.now(clock);
            ProjectData.Data data = projectData.load(key, asOf);
            Release rel = AnalysisTools.requireRelease(data, key, release);
            Map<String, Object> report = new LinkedHashMap<>(reportEngine.build(data.issues(), rel.name(), asOf,
                    data.team(), data.absences(), audience));
            // The engine does not know release dates: give the report's forecast the release date as its target.
            Forecast forecast = forecastEngine.forecast(data.issues(), rel.name(), asOf, data.team(), data.absences(),
                    rel.releaseDate());
            report.put("forecast", forecast);
            report.put("windowStart", forecast.windowStart().toString());
            report.put("windowEnd", forecast.windowEnd().toString());
            return report;
        });
    }
}
