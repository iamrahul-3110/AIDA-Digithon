package com.aida.mock;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aida.contract.Issue;
import com.aida.contract.Release;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;

/** Jira REST v2 look-alike over the generated data: search with changelog, and project versions. */
@RestController
@Profile("mock")
@RequestMapping("/mock/jira/rest/api/2")
public class MockJiraController {

    static final int MAX_RESULTS = 100;

    /** {@code project=KEY} with an optional {@code AND fixVersion="NAME"}; the only JQL the adapters send. */
    private static final Pattern JQL = Pattern.compile(
            "\\s*project\\s*=\\s*\"?([A-Za-z][A-Za-z0-9_]*)\"?\\s*(?:AND\\s+fixVersion\\s*=\\s*(?:\"([^\"]*)\"|(\\S+))\\s*)?",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter JIRA_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(DataGenerator.ZONE);

    private final MockStore store;

    public MockJiraController(MockStore store) {
        this.store = store;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String jql, @RequestParam(defaultValue = "0") int startAt,
            @RequestParam(defaultValue = "50") int maxResults) {
        Matcher m = JQL.matcher(jql);
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported JQL: " + jql);
        }
        String project = m.group(1).toUpperCase();
        String fixVersion = m.group(2) != null ? m.group(2) : m.group(3);
        int start = Math.max(0, startAt);
        int size = Math.clamp(maxResults, 0, MAX_RESULTS);

        List<Issue> matches = store.dataset().issues().stream()
                .filter(i -> i.project().equals(project))
                .filter(i -> fixVersion == null || fixVersion.equals(i.fixVersion()))
                .sorted(Comparator.comparingInt(MockJiraController::keyNumber))
                .toList();
        List<Map<String, Object>> page = matches.stream().skip(start).limit(size).map(MockJiraController::toJira)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startAt", start);
        body.put("maxResults", size);
        body.put("total", matches.size());
        body.put("issues", page);
        return body;
    }

    @GetMapping("/project/{key}/versions")
    public List<Map<String, Object>> versions(@PathVariable String key) {
        return store.dataset().versions().stream()
                .filter(r -> r.project().equals(key.toUpperCase()))
                .map(MockJiraController::toJira)
                .toList();
    }

    static Map<String, Object> toJira(Release release) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("name", release.name());
        v.put("releaseDate", release.releaseDate().toString());
        v.put("released", release.released());
        return v;
    }

    static Map<String, Object> toJira(Issue issue) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", issue.summary());
        fields.put("issuetype", Map.of("name", issue.type()));
        fields.put("status", Map.of("name", issue.status()));
        fields.put("created", time(issue.statusHistory().getFirst().at()));
        fields.put("resolutiondate", issue.status().equals(Statuses.DONE) ? time(issue.statusHistory().getLast().at())
                : null);
        fields.put("customfield_10016", issue.estimate());
        fields.put("fixVersions", issue.fixVersion() == null ? List.of() : List.of(Map.of("name", issue.fixVersion())));
        fields.put("assignee", issue.assignee() == null ? null : Map.of("displayName", issue.assignee()));
        fields.put("parent", issue.epic() == null ? null
                : Map.of("key", issue.epic(), "fields", Map.of("summary", issue.epicName())));

        List<Map<String, Object>> histories = new ArrayList<>();
        List<StatusChange> changes = issue.statusHistory();
        for (int i = 1; i < changes.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", "status");
            item.put("fromString", changes.get(i - 1).status());
            item.put("toString", changes.get(i).status());
            histories.add(Map.of("created", time(changes.get(i).at()), "items", List.of(item)));
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", issue.key());
        json.put("fields", fields);
        json.put("changelog", Map.of("histories", histories));
        return json;
    }

    private static String time(Instant instant) {
        return JIRA_TIME.format(instant);
    }

    private static int keyNumber(Issue issue) {
        return Integer.parseInt(issue.key().substring(issue.key().indexOf('-') + 1));
    }
}
