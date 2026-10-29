package com.aida.mock;

import com.aida.contract.Absence;
import com.aida.contract.Person;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock leave-calendar and team endpoints.
 */
@RestController
@RequestMapping("/mock")
public class MockCalendarController {

    private final DataGenerator generator;

    public MockCalendarController(DataGenerator generator) {
        this.generator = generator;
    }

    @GetMapping("/team")
    public List<Map<String, String>> team(@RequestParam(required = false) String project) {
        List<Person> team = generator.getTeam(project != null ? project : "SHOP");
        return team.stream().map(p -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("country", p.country());
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/calendar/events")
    public Map<String, Object> events(
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);

        List<Absence> filtered = generator.getAbsences().stream()
            .filter(a -> !a.end().isBefore(fromDate) && !a.start().isAfter(toDate))
            .collect(Collectors.toList());

        List<Map<String, Object>> events = filtered.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("person", a.person());
            m.put("country", a.country());
            m.put("start", a.start().toString());
            m.put("end", a.end().toString());
            m.put("type", a.type());
            return m;
        }).collect(Collectors.toList());

        return Map.of("events", events);
    }

    @PostMapping("/calendar/events")
    public ResponseEntity<Map<String, Object>> addLeave(@RequestBody Map<String, String> body) {
        String person = body.get("person");
        LocalDate start = LocalDate.parse(body.get("start"));
        LocalDate end = LocalDate.parse(body.get("end"));

        // resolve country from team
        String country = generator.getTeam("SHOP").stream()
            .filter(p -> p.name().equals(person))
            .map(Person::country)
            .findFirst()
            .orElseGet(() -> generator.getTeam("OPS").stream()
                .filter(p -> p.name().equals(person))
                .map(Person::country)
                .findFirst()
                .orElse("IN"));

        Absence absence = generator.addLeave(person, country, start, end);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", absence.id());
        result.put("person", absence.person());
        result.put("country", absence.country());
        result.put("start", absence.start().toString());
        result.put("end", absence.end().toString());
        result.put("type", absence.type());
        return ResponseEntity.status(201).body(result);
    }

    @DeleteMapping("/calendar/events/{id}")
    public ResponseEntity<Void> deleteLeave(@PathVariable String id) {
        boolean removed = generator.deleteLeave(id);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
