package com.aida.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aida.contract.Absence;
import com.aida.contract.Driver;
import com.aida.contract.Forecast;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Risk;
import com.aida.contract.Statuses;

/** Status report data per audience. Pure and stateless. Signature FROZEN; implemented by Person B in M6 (B5). */
public class ReportEngine {

    public static final List<String> AUDIENCES = List.of("team", "management", "client", "hq");

    /** The keys an hq report may contain. */
    public static final Set<String> HQ_KEYS =
            Set.of("audience", "project", "release", "asOf", "forecast", "progress", "topDrivers");

    static final String SCRUBBED_NAME = "a team member";

    private final ForecastEngine forecastEngine = new ForecastEngine();
    private final ExplainEngine explainEngine = new ExplainEngine();
    private final RiskEngine riskEngine = new RiskEngine();

    /**
     * {audience, project, release, asOf, forecast, progress {done, remaining, total}, topDrivers (explain over the
     * last 7 days, top 3), risks (top 5, release items), asks (release items waiting on the client)}.
     * team and management get everything; hq gets forecast, progress and top drivers only; client gets no team-side
     * risks, no assignees and no team member names anywhere (names in sentences become "a team member").
     * The forecast has no target date (the engine does not know release dates); callers may add one.
     *
     * @param audience team, management, client or hq
     */
    public Map<String, Object> build(List<Issue> issues, String release, LocalDate asOf, List<Person> team,
            List<Absence> absences, String audience) {
        String who = audience == null ? "" : audience.trim().toLowerCase(Locale.ROOT);
        if (!AUDIENCES.contains(who)) {
            throw new IllegalArgumentException("audience must be one of " + AUDIENCES + ": " + audience);
        }
        Forecast forecast = forecastEngine.forecast(issues, release, asOf, team, absences, null);
        List<Issue> scope = Snapshots.snapshot(issues, asOf).stream().filter(i -> release.equals(i.fixVersion()))
                .toList();
        long done = scope.stream().filter(i -> Statuses.DONE.equals(i.status())).count();
        List<Driver> drivers = explainEngine.explain(issues, release, asOf.minusDays(7), asOf, team, absences)
                .drivers().stream().limit(3).toList();
        List<Risk> risks = riskEngine.assess(scope, asOf).items();
        boolean client = who.equals("client");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("audience", who);
        report.put("project", forecast.project());
        report.put("release", release);
        report.put("asOf", asOf);
        report.put("forecast", forecast);
        report.put("progress", progress(done, scope.size()));
        report.put("topDrivers", drivers.stream().map(this::driver).toList());
        if (who.equals("hq")) {
            return report;
        }
        report.put("risks", risks.stream().filter(r -> !client || "client".equals(r.side())).limit(5)
                .map(r -> risk(r, client)).toList());
        report.put("asks", scope.stream().filter(i -> Statuses.isClientSide(i.status()))
                .sorted(Comparator.comparing(Issue::key)).map(i -> ask(i, asOf)).toList());
        if (client) {
            return scrub(report, names(team, issues));
        }
        return report;
    }

    private static Map<String, Object> progress(long done, int total) {
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("done", done);
        progress.put("remaining", total - done);
        progress.put("total", total);
        return progress;
    }

    private Map<String, Object> driver(Driver d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", d.type());
        m.put("impactDays", d.impactDays());
        m.put("detail", d.detail());
        m.put("keys", d.keys());
        return m;
    }

    private static Map<String, Object> risk(Risk r, boolean client) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", r.key());
        m.put("summary", r.summary());
        m.put("reason", r.reason());
        m.put("side", r.side());
        m.put("days", r.days());
        if (!client) {
            m.put("assignee", r.assignee());
        }
        return m;
    }

    private static Map<String, Object> ask(Issue i, LocalDate asOf) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", i.key());
        m.put("summary", i.summary());
        m.put("status", i.status());
        m.put("daysWaiting", RiskEngine.daysInStatus(i, asOf));
        return m;
    }

    /** Team members and every assignee, longest first so "Min-jun" goes before a shorter overlapping name. */
    private static List<String> names(List<Person> team, List<Issue> issues) {
        Set<String> names = new LinkedHashSet<>();
        if (team != null) {
            team.stream().map(Person::name).filter(Objects::nonNull).forEach(names::add);
        }
        issues.stream().map(Issue::assignee).filter(Objects::nonNull).forEach(names::add);
        return names.stream().filter(n -> !n.isBlank()).sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    /** Deep copy with every name replaced in every string. */
    @SuppressWarnings("unchecked")
    private static <T> T scrub(T value, List<String> names) {
        if (value instanceof String s) {
            return (T) scrubText(s, names);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put((String) k, scrub(v, names)));
            return (T) out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(scrub(v, names)));
            return (T) out;
        }
        return value;
    }

    static String scrubText(String text, List<String> names) {
        String out = text;
        for (String name : names) {
            out = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(out)
                    .replaceAll(Matcher.quoteReplacement(SCRUBBED_NAME));
        }
        return out;
    }
}
