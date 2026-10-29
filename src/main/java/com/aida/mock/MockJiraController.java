package com.aida.mock;

import com.aida.contract.Issue;
import com.aida.contract.StatusChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock Jira REST API (Jira-shaped responses).
 */
@RestController
@RequestMapping("/mock/jira")
public class MockJiraController {

    private final DataGenerator generator;

    public MockJiraController(DataGenerator generator) {
        this.generator = generator;
    }

    @GetMapping("/rest/api/2/search")
    public Map<String, Object> search(
            @RequestParam String jql,
            @RequestParam(defaultValue = "0") int startAt,
            @RequestParam(defaultValue = "100") int maxResults) {

        // Parse JQL: project=SHOP AND fixVersion="3.2"
        String project = null;
        String fixVersion = null;
        for (String clause : jql.split(" AND ")) {
            clause = clause.trim();
            if (clause.startsWith("project=")) {
                project = clause.substring("project=".length()).trim().replaceAll("\"", "");
            } else if (clause.startsWith("fixVersion=")) {
                fixVersion = clause.substring("fixVersion=".length()).trim().replaceAll("\"", "");
            }
        }

        List<Issue> all = generator.getIssues(project != null ? project : "SHOP");
        final String fv = fixVersion;
        if (fv != null) {
            all = all.stream().filter(i -> fv.equals(i.fixVersion())).collect(Collectors.toList());
        }

        int total = all.size();
        List<Issue> page = all.stream().skip(startAt).limit(maxResults).collect(Collectors.toList());
        List<Map<String, Object>> issues = page.stream().map(this::toJiraShape).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startAt", startAt);
        result.put("maxResults", maxResults);
        result.put("total", total);
        result.put("issues", issues);
        return result;
    }

    @GetMapping("/rest/api/2/project/{key}/versions")
    public List<Map<String, Object>> versions(@PathVariable String key) {
        return generator.getReleases(key).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name());
            m.put("releaseDate", r.releaseDate() != null ? r.releaseDate().toString() : null);
            m.put("released", r.released());
            return m;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> toJiraShape(Issue i) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", i.summary());
        fields.put("issuetype", Map.of("name", i.type()));
        fields.put("status", Map.of("name", i.status()));
        fields.put("created", i.created() != null ? i.created().toString() + "T09:00:00.000+0530" : null);
        fields.put("resolutiondate", i.resolved() != null ? i.resolved().toString() + "T18:00:00.000+0530" : null);
        fields.put("customfield_10016", i.estimate());
        fields.put("fixVersions", i.fixVersion() != null ? List.of(Map.of("name", i.fixVersion())) : List.of());
        fields.put("assignee", i.assignee() != null ? Map.of("displayName", i.assignee()) : null);
        if (i.epic() != null) {
            fields.put("parent", Map.of("key", i.epic(), "fields", Map.of("summary", i.epicName() != null ? i.epicName() : "")));
        }

        // Build changelog
        List<Map<String, Object>> histories = new ArrayList<>();
        if (i.statusHistory() != null) {
            // Group by instant (each history entry = one status change)
            for (int idx = 1; idx < i.statusHistory().size(); idx++) {
                StatusChange sc = i.statusHistory().get(idx);
                StatusChange prev = i.statusHistory().get(idx - 1);
                Map<String, Object> hist = new LinkedHashMap<>();
                hist.put("created", sc.at().toString());
                hist.put("items", List.of(Map.of(
                    "field", "status",
                    "fromString", prev.status(),
                    "toString", sc.status()
                )));
                histories.add(hist);
            }
        }

        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("key", i.key());
        issue.put("fields", fields);
        issue.put("changelog", Map.of("histories", histories));
        return issue;
    }
}
