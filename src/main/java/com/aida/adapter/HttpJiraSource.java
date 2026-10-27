package com.aida.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aida.contract.Issue;
import com.aida.contract.Release;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jira REST v2 over HTTP: {@code /rest/api/2/search} with changelog, paged 100 at a time, and project versions.
 * Points at the mock in the MVP ({@code aida.jira.base-url}); real Jira later is a configuration change.
 */
@Component
public class HttpJiraSource implements JiraSource {

    static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter JIRA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final RestClient http;

    public HttpJiraSource(@Value("${aida.jira.base-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public List<Issue> issues(String project, String release) {
        String jql = "project=" + project + (release == null ? "" : " AND fixVersion=\"" + release + "\"");
        List<Issue> issues = new ArrayList<>();
        int startAt = 0;
        while (true) {
            int start = startAt;
            SearchPage page = http.get()
                    .uri(u -> u.path("/rest/api/2/search")
                            .queryParam("jql", "{jql}")
                            .queryParam("startAt", start)
                            .queryParam("maxResults", PAGE_SIZE)
                            .queryParam("expand", "changelog")
                            .build(Map.of("jql", jql)))
                    .retrieve()
                    .body(SearchPage.class);
            if (page == null || page.issues() == null || page.issues().isEmpty()) {
                break;
            }
            page.issues().forEach(i -> issues.add(toIssue(project, i)));
            startAt += page.issues().size();
            if (startAt >= page.total()) {
                break;
            }
        }
        return issues;
    }

    @Override
    public List<Release> releases(String project) {
        List<JiraVersion> versions = http.get()
                .uri("/rest/api/2/project/{key}/versions", project)
                .retrieve()
                .body(new ParameterizedTypeReference<List<JiraVersion>>() {
                });
        if (versions == null) {
            return List.of();
        }
        return versions.stream()
                .map(v -> new Release(project, v.name(), v.releaseDate() == null ? null : LocalDate.parse(v.releaseDate()),
                        v.released()))
                .toList();
    }

    /** Maps one Jira issue; statusHistory starts with "To Do" at creation, then every status change in order. */
    static Issue toIssue(String project, JiraIssue issue) {
        Fields f = issue.fields();
        OffsetDateTime created = parse(f.created());
        List<StatusChange> history = new ArrayList<>();
        history.add(new StatusChange(Statuses.TO_DO, created.toInstant()));
        if (issue.changelog() != null && issue.changelog().histories() != null) {
            issue.changelog().histories().stream()
                    .sorted(Comparator.comparing(h -> parse(h.created()).toInstant()))
                    .forEach(h -> {
                        Instant at = parse(h.created()).toInstant();
                        for (Item item : h.items()) {
                            if ("status".equals(item.field())) {
                                history.add(new StatusChange(item.to(), at));
                            }
                        }
                    });
        }
        return new Issue(
                issue.key(),
                project,
                f.issuetype() == null ? null : f.issuetype().name(),
                f.summary(),
                f.status() == null ? history.getLast().status() : f.status().name(),
                List.copyOf(history),
                created.toLocalDate(),
                f.resolutiondate() == null ? null : parse(f.resolutiondate()).toLocalDate(),
                f.estimate(),
                f.parent() == null ? null : f.parent().key(),
                f.parent() == null || f.parent().fields() == null ? null : f.parent().fields().summary(),
                f.fixVersions() == null || f.fixVersions().isEmpty() ? null : f.fixVersions().getFirst().name(),
                f.assignee() == null ? null : f.assignee().displayName());
    }

    private static OffsetDateTime parse(String jiraTime) {
        return OffsetDateTime.parse(jiraTime, JIRA_TIME);
    }

    // Jira JSON, only the parts AIDA reads.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchPage(int startAt, int maxResults, int total, List<JiraIssue> issues) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraIssue(String key, Fields fields, Changelog changelog) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fields(String summary, Named issuetype, Named status, String created, String resolutiondate,
            @JsonProperty("customfield_10016") Double estimate, List<Named> fixVersions, Assignee assignee,
            Parent parent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Named(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Assignee(String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Parent(String key, ParentFields fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParentFields(String summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Changelog(List<History> histories) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record History(String created, List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String field, String fromString, @JsonProperty("toString") String to) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraVersion(String name, String releaseDate, boolean released) {
    }
}
