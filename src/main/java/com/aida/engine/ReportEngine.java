package com.aida.engine;

import com.aida.contract.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Report builder for 4 audiences: team, management, client, hq.
 */
public class ReportEngine {

    private ReportEngine() {}

    public static Map<String, Object> build(
            List<Issue> issues,
            String release,
            LocalDate asOf,
            List<Person> team,
            List<Absence> absences,
            String audience) {

        List<Issue> snap = Snapshots.snapshot(issues, asOf);
        List<Issue> releaseIssues = snap.stream()
            .filter(i -> release.equals(i.fixVersion()))
            .collect(Collectors.toList());

        long done = releaseIssues.stream().filter(i -> "Done".equals(i.status())).count();
        long remaining = releaseIssues.size() - done;

        Forecast forecast = ForecastEngine.forecast(issues, release, asOf, team, absences, null, 10000, 42);
        RiskReport riskReport = RiskEngine.assess(releaseIssues, asOf);
        ChangeExplanation explain = ExplainEngine.explain(issues, release, asOf.minusDays(7), asOf, team, absences);

        List<Driver> topDrivers = explain.drivers().stream()
            .sorted(Comparator.comparingDouble(d -> -Math.abs(d.impactDays())))
            .limit(3).collect(Collectors.toList());

        List<Risk> topRisks = riskReport.items().stream().limit(5).collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("audience", audience);
        report.put("project", forecast.project());
        report.put("release", release);
        report.put("asOf", asOf.toString());
        report.put("forecast", stripForAudience(forecast, audience));
        report.put("progress", Map.of("done", done, "remaining", remaining, "total", releaseIssues.size()));
        report.put("topDrivers", stripDriversForAudience(topDrivers, audience));

        if ("client".equals(audience)) {
            // No assignee names, no team-side risks; client-facing asks
            List<Map<String, Object>> asks = snap.stream()
                .filter(i -> "Waiting for Client UAT".equals(i.status()))
                .map(i -> Map.<String, Object>of("key", i.key(), "summary", i.summary()))
                .collect(Collectors.toList());
            report.put("risks", topRisks.stream()
                .filter(r -> "client".equals(r.side()))
                .map(r -> Map.<String, Object>of("key", r.key(), "summary", r.summary(), "days", r.days(), "reason", r.reason()))
                .collect(Collectors.toList()));
            report.put("asks", asks);
        } else if ("hq".equals(audience)) {
            // forecast, progress, top3 drivers only
            // already set above – remove risks
        } else {
            // team / management – full risks
            report.put("risks", topRisks.stream()
                .map(r -> Map.<String, Object>of(
                    "key", r.key(), "summary", r.summary(), "reason", r.reason(),
                    "side", r.side(), "days", r.days(), "assignee", r.assignee() != null ? r.assignee() : ""))
                .collect(Collectors.toList()));
        }

        return report;
    }

    private static Map<String, Object> stripForAudience(Forecast f, String audience) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("p50", f.p50());
        m.put("p85", f.p85());
        m.put("p95", f.p95());
        m.put("pByTarget", f.pByTarget());
        m.put("targetDate", f.targetDate());
        m.put("remainingItems", f.remainingItems());
        if (!"client".equals(audience) && !"hq".equals(audience)) {
            m.put("capacityFactor", f.capacityFactor());
            m.put("sampleWeeks", f.sampleWeeks());
        }
        return m;
    }

    private static List<Map<String, Object>> stripDriversForAudience(List<Driver> drivers, String audience) {
        return drivers.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", d.type());
            m.put("impactDays", Math.round(d.impactDays()));
            m.put("detail", d.detail());
            if (!"client".equals(audience) && !"hq".equals(audience)) {
                m.put("keys", d.keys());
            }
            return m;
        }).collect(Collectors.toList());
    }
}
