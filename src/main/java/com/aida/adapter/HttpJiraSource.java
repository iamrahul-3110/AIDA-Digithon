package com.aida.adapter;

import com.aida.contract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class HttpJiraSource implements JiraSource {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${aida.jira.base-url:http://localhost:8080/mock/jira}")
    private String baseUrl;

    public HttpJiraSource(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Issue> issues(String project, String release) {
        List<Issue> result = new ArrayList<>();
        int startAt = 0;
        int maxResults = 100;
        String jql = "project=" + project + (release != null ? " AND fixVersion=\"" + release + "\"" : "");

        while (true) {
            String url = baseUrl + "/rest/api/2/search?jql=" + jql + "&startAt=" + startAt + "&maxResults=" + maxResults;
            try {
                String body = restClient.get().uri(url).retrieve().body(String.class);
                JsonNode root = objectMapper.readTree(body);
                JsonNode issuesNode = root.path("issues");
                if (!issuesNode.isArray() || issuesNode.size() == 0) break;
                for (JsonNode node : issuesNode) {
                    result.add(parseIssue(node, project));
                }
                int total = root.path("total").asInt();
                startAt += issuesNode.size();
                if (startAt >= total) break;
            } catch (Exception e) {
                break;
            }
        }
        return result;
    }

    @Override
    public List<Release> releases(String project) {
        String url = baseUrl + "/rest/api/2/project/" + project + "/versions";
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode arr = objectMapper.readTree(body);
            List<Release> releases = new ArrayList<>();
            for (JsonNode node : arr) {
                String name = node.path("name").asText();
                String releaseDateStr = node.path("releaseDate").asText(null);
                LocalDate releaseDate = releaseDateStr != null ? LocalDate.parse(releaseDateStr) : null;
                boolean released = node.path("released").asBoolean(false);
                releases.add(new Release(project, name, releaseDate, released));
            }
            return releases;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Issue parseIssue(JsonNode node, String project) {
        String key = node.path("key").asText();
        JsonNode fields = node.path("fields");

        String type = fields.path("issuetype").path("name").asText("Story");
        String summary = fields.path("summary").asText();
        String statusName = fields.path("status").path("name").asText("To Do");
        String createdStr = fields.path("created").asText(null);
        LocalDate created = parseDate(createdStr);
        String resolvedStr = fields.path("resolutiondate").asText(null);
        LocalDate resolved = (resolvedStr != null && !resolvedStr.isBlank() && !"null".equals(resolvedStr)) ? parseDate(resolvedStr) : null;
        Double estimate = fields.path("customfield_10016").isNull() ? null : fields.path("customfield_10016").asDouble();
        if (fields.path("customfield_10016").isMissingNode()) estimate = null;

        String epic = fields.path("parent").path("key").asText(null);
        String epicName = fields.path("parent").path("fields").path("summary").asText(null);

        String fixVersion = null;
        JsonNode fv = fields.path("fixVersions");
        if (fv.isArray() && fv.size() > 0) fixVersion = fv.get(0).path("name").asText(null);

        String assignee = fields.path("assignee").path("displayName").asText(null);

        // Build status history
        List<StatusChange> history = new ArrayList<>();
        if (created != null) {
            history.add(new StatusChange("To Do", created.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));
        }
        JsonNode changelog = node.path("changelog").path("histories");
        if (changelog.isArray()) {
            for (JsonNode h : changelog) {
                String changedAt = h.path("created").asText(null);
                Instant at = changedAt != null ? parseInstant(changedAt) : null;
                for (JsonNode item : h.path("items")) {
                    if ("status".equals(item.path("field").asText())) {
                        String toStatus = item.path("toString").asText();
                        if (at != null) history.add(new StatusChange(toStatus, at));
                    }
                }
            }
        }
        history.sort(Comparator.comparing(StatusChange::at));

        return new Issue(key, project, type, summary, statusName, history, created, resolved,
            estimate, epic, epicName, fixVersion, assignee);
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank() || "null".equals(s)) return null;
        try {
            // Jira format: 2026-09-01T09:00:00.000+0530
            return Instant.parse(s.length() == 10 ? s + "T00:00:00Z" :
                s.replaceAll("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3})(\\+|-)([0-9]{4})$", "$1$2$3:00")
                 .replaceAll("(\\+|-)([0-9]{2})([0-9]{2})$", "$1$2:$3"))
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception ex) { return null; }
        }
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String fixed = s.replaceAll("(\\+|-)([0-9]{2})([0-9]{2})$", "$1$2:$3");
            return Instant.parse(fixed);
        } catch (Exception e) {
            try { return LocalDate.parse(s.substring(0, 10)).atStartOfDay().toInstant(java.time.ZoneOffset.UTC); } catch (Exception ex) { return Instant.now(); }
        }
    }
}
