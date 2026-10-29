package com.aida.adapter;

import com.aida.contract.Absence;
import com.aida.contract.Person;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class HttpLeaveSource implements LeaveSource {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${aida.leave.base-url:http://localhost:8080/mock}")
    private String baseUrl;

    public HttpLeaveSource(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Person> team(String project) {
        String url = baseUrl + "/team?project=" + project;
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode arr = objectMapper.readTree(body);
            List<Person> result = new ArrayList<>();
            for (JsonNode node : arr) {
                result.add(new Person(node.path("name").asText(), node.path("country").asText()));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<Absence> absences(LocalDate from, LocalDate to) {
        String url = baseUrl + "/calendar/events?from=" + from + "&to=" + to;
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.path("events");
            List<Absence> result = new ArrayList<>();
            if (events.isArray()) {
                for (JsonNode node : events) {
                    String id = node.path("id").asText();
                    String personNode = node.path("person").asText(null);
                    String person = (personNode == null || personNode.isBlank() || "null".equals(personNode)) ? null : personNode;
                    String country = node.path("country").asText(null);
                    LocalDate start = LocalDate.parse(node.path("start").asText());
                    LocalDate end = LocalDate.parse(node.path("end").asText());
                    String type = node.path("type").asText("leave");
                    result.add(new Absence(id, person, country, start, end, type));
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
