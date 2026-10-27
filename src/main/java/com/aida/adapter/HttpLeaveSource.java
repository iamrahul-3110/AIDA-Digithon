package com.aida.adapter;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aida.contract.Absence;
import com.aida.contract.Person;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Team and leave calendar over HTTP ({@code aida.leave.base-url}): the mock in the MVP, the Confluence team
 * calendar later.
 */
@Component
public class HttpLeaveSource implements LeaveSource {

    private final RestClient http;

    public HttpLeaveSource(@Value("${aida.leave.base-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public List<Person> team(String project) {
        List<Person> team = http.get()
                .uri(u -> u.path("/team").queryParam("project", project).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Person>>() {
                });
        return team == null ? List.of() : List.copyOf(team);
    }

    @Override
    public List<Absence> absences(LocalDate from, LocalDate to) {
        Events events = http.get()
                .uri(u -> u.path("/calendar/events").queryParam("from", from).queryParam("to", to).build())
                .retrieve()
                .body(Events.class);
        return events == null || events.events() == null ? List.of() : List.copyOf(events.events());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Events(List<Absence> events) {
    }
}
