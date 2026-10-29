package com.aida.mcp;

import com.aida.adapter.JiraSource;
import com.aida.adapter.LeaveSource;
import com.aida.contract.*;
import com.aida.engine.*;
import com.aida.security.CurrentPrincipalHolder;
import com.aida.security.TokenPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class AidaTools {

    private final JiraSource jiraSource;
    private final LeaveSource leaveSource;
    private final AuditLog auditLog;

    public AidaTools(JiraSource jiraSource, LeaveSource leaveSource, AuditLog auditLog) {
        this.jiraSource = jiraSource;
        this.leaveSource = leaveSource;
        this.auditLog = auditLog;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TokenPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TokenPrincipal tp) return tp;
        TokenPrincipal fallback = CurrentPrincipalHolder.get();
        if (fallback != null) return fallback;
        return null;
    }

    private Map<String, Object> accessDenied(String project, TokenPrincipal tp) {
        String name = tp != null ? tp.name() : "anonymous";
        return Map.of("error", "Access denied: token '" + name + "' cannot read project " + project);
    }

    private Map<String, Object> meta(String tool, LocalDate asOf, LocalDate windowStart, LocalDate windowEnd) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool", tool);
        m.put("calledAt", java.time.Instant.now().toString());
        m.put("asOf", asOf != null ? asOf.toString() : null);
        m.put("windowStart", windowStart != null ? windowStart.toString() : null);
        m.put("windowEnd", windowEnd != null ? windowEnd.toString() : null);
        return m;
    }

    // ── Tools ─────────────────────────────────────────────────────────────────

    @Tool(description = "Forecast the delivery date for a project release using Monte Carlo simulation. " +
        "Returns p50/p85/p95 finish dates and probability of shipping by the target date.")
    public Map<String, Object> forecast_delivery(
            @ToolParam(description = "Jira project key, e.g. SHOP or OPS") String project,
            @ToolParam(description = "Release name, e.g. 3.2 or Q4") String release,
            @ToolParam(description = "As-of date ISO-8601 (defaults to today)") String asOf,
            @ToolParam(description = "Target ship date ISO-8601 (defaults to the release's planned date)") String targetDate) {

        TokenPrincipal tp = principal();
        if (tp == null || !tp.canReadProject(project)) return accessDenied(project, tp);

        LocalDate asOfDate = asOf != null ? LocalDate.parse(asOf) : LocalDate.now();
        List<Issue> issues = jiraSource.issues(project, release);
        List<Person> team = leaveSource.team(project);
        List<Release> releases = jiraSource.releases(project);
        List<Absence> absences = leaveSource.absences(asOfDate.minusMonths(1), asOfDate.plusMonths(6));

        LocalDate target = null;
        if (targetDate != null) {
            target = LocalDate.parse(targetDate);
        } else {
            target = releases.stream()
                .filter(r -> release.equals(r.name()))
                .map(Release::releaseDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }

        Forecast forecast = ForecastEngine.forecast(issues, release, asOfDate, team, absences, target, 10000, 42);

        Map<String, Object> args = Map.of("project", project, "release", release, "asOf", asOfDate.toString());
        auditLog.write(tp.name(), "forecast_delivery", args,
            "p85=" + forecast.p85() + " pByTarget=" + forecast.pByTarget());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta("forecast_delivery", asOfDate, forecast.windowStart(), forecast.windowEnd()));
        result.put("forecast", forecast);
        return result;
    }

    @Tool(description = "Check data quality for a project. Returns a score 0-100, list of quality issues, " +
        "and whether a reliable forecast is possible.")
    public Map<String, Object> check_data_quality(
            @ToolParam(description = "Jira project key, e.g. SHOP or OPS") String project) {

        TokenPrincipal tp = principal();
        if (tp == null || !tp.canReadProject(project)) return accessDenied(project, tp);

        LocalDate asOf = LocalDate.now();
        List<Issue> issues = jiraSource.issues(project, null);
        DataQuality quality = QualityEngine.assess(issues, asOf);

        Map<String, Object> args = Map.of("project", project);
        auditLog.write(tp.name(), "check_data_quality", args, "score=" + quality.score() + " forecastAllowed=" + quality.forecastAllowed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta("check_data_quality", asOf, null, null));
        result.put("quality", quality);
        return result;
    }

    @Tool(description = "Explain why the delivery date shifted between two dates. Identifies drivers like scope changes, " +
        "client wait, leave, throughput drop, and aging items.")
    public Map<String, Object> explain_change(
            @ToolParam(description = "Jira project key") String project,
            @ToolParam(description = "Release name") String release,
            @ToolParam(description = "Start date ISO-8601") String fromDate,
            @ToolParam(description = "End date ISO-8601") String toDate) {

        TokenPrincipal tp = principal();
        if (tp == null || !tp.canReadProject(project)) return accessDenied(project, tp);

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);
        List<Issue> issues = jiraSource.issues(project, null);
        List<Person> team = leaveSource.team(project);
        List<Absence> absences = leaveSource.absences(from.minusWeeks(4), to.plusWeeks(4));

        ChangeExplanation explanation = ExplainEngine.explain(issues, release, from, to, team, absences);

        Map<String, Object> args = Map.of("project", project, "release", release, "fromDate", fromDate, "toDate", toDate);
        auditLog.write(tp.name(), "explain_change", args, "shiftDays=" + explanation.shiftDays() + " drivers=" + explanation.drivers().size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta("explain_change", to, from, to));
        result.put("explanation", explanation);
        return result;
    }

    @Tool(description = "Run a what-if scenario: drop epics, add/remove people, or change target date. " +
        "Returns a new forecast showing the impact.")
    public Map<String, Object> run_what_if(
            @ToolParam(description = "Jira project key") String project,
            @ToolParam(description = "Release name") String release,
            @ToolParam(description = "Epic keys or names to drop from scope (comma-separated, optional)") String dropEpics,
            @ToolParam(description = "People to add as JSON array [{name,country}], optional") String addPeople,
            @ToolParam(description = "People names to remove (comma-separated, optional)") String removePeople,
            @ToolParam(description = "New target date ISO-8601 (optional)") String targetDate) {

        TokenPrincipal tp = principal();
        if (tp == null || !tp.canReadProject(project)) return accessDenied(project, tp);

        LocalDate asOf = LocalDate.now();
        List<Issue> issues = jiraSource.issues(project, null);
        List<Person> team = leaveSource.team(project);
        List<Absence> absences = leaveSource.absences(asOf.minusMonths(1), asOf.plusMonths(6));

        List<String> epics = dropEpics != null ? Arrays.asList(dropEpics.split(",")) : List.of();
        List<String> remove = removePeople != null ? Arrays.asList(removePeople.split(",")) : List.of();
        List<Person> add = List.of();
        if (addPeople != null && !addPeople.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Person[] arr = om.readValue(addPeople, Person[].class);
                add = Arrays.asList(arr);
            } catch (Exception ignored) {}
        }
        LocalDate target = targetDate != null ? LocalDate.parse(targetDate) : null;

        WhatIf scenario = new WhatIf(epics, add, remove, target);
        Forecast forecast = WhatIfEngine.run(issues, release, asOf, team, absences, scenario);

        Map<String, Object> args = Map.of("project", project, "release", release, "dropEpics", epics);
        auditLog.write(tp.name(), "run_what_if", args, "p85=" + forecast.p85() + " pByTarget=" + forecast.pByTarget());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta("run_what_if", asOf, forecast.windowStart(), forecast.windowEnd()));
        result.put("forecast", forecast);
        return result;
    }

    @Tool(description = "Get delivery risks for a project: aging items, client-wait blockers, and stale stories. " +
        "Returns items sorted by days in current status.")
    public Map<String, Object> get_risks(
            @ToolParam(description = "Jira project key") String project) {

        TokenPrincipal tp = principal();
        if (tp == null || !tp.canReadProject(project)) return accessDenied(project, tp);

        LocalDate asOf = LocalDate.now();
        List<Issue> issues = jiraSource.issues(project, null);
        RiskReport report = RiskEngine.assess(issues, asOf);

        Map<String, Object> args = Map.of("project", project);
        auditLog.write(tp.name(), "get_risks", args, "risks=" + report.items().size() + " bottleneck=" + report.bottleneckStage());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta("get_risks", asOf, null, null));
        result.put("report", report);
        return result;
    }

    @Tool(description = "Build a status report for a specific audience: team, management, client, or hq. " +
        "Client reports exclude assignee names and internal risks.")
    public Map<String, Object> build_report(
            @ToolParam(description = "Jira project key") String project,
            @ToolParam(description = "Release name") String release,
            @ToolParam(description = "Audience: team | management | client | hq") String audience) {

        TokenPrincipal tp = principal();
        if (tp == null || !tp.canReadProject(project)) return accessDenied(project, tp);

        LocalDate asOf = LocalDate.now();
        List<Issue> issues = jiraSource.issues(project, null);
        List<Person> team = leaveSource.team(project);
        List<Absence> absences = leaveSource.absences(asOf.minusMonths(1), asOf.plusMonths(6));

        Map<String, Object> report = ReportEngine.build(issues, release, asOf, team, absences, audience);

        Map<String, Object> args = Map.of("project", project, "release", release, "audience", audience);
        auditLog.write(tp.name(), "build_report", args, "audience=" + audience);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta("build_report", asOf, null, null));
        result.put("report", report);
        return result;
    }

    @Tool(description = "Verify an answer draft against recent audit log entries. Extracts numbers, percentages, " +
        "and dates from the draft and checks them against what the tools actually returned in the last 30 minutes.")
    public Map<String, Object> verify_answer(
            @ToolParam(description = "The draft answer text to verify") String draft) {

        TokenPrincipal tp = principal();

        List<Map<String, Object>> recent = auditLog.getRecentEntries(30);
        List<String> unmatched = Guardrail.verify(draft, recent);

        Map<String, Object> args = Map.of("draft_length", draft.length());
        if (tp != null) auditLog.write(tp.name(), "verify_answer", args, "unmatched=" + unmatched.size());

        return Map.of("ok", unmatched.isEmpty(), "unmatched", unmatched);
    }
}
