package com.aida.mock;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.aida.contract.Absence;
import com.aida.contract.Person;

/** Team list and leave calendar look-alike (Confluence team calendar in the real setup). */
@RestController
@Profile("mock")
@RequestMapping("/mock")
public class MockCalendarController {

    /** Body of POST /mock/calendar/events. */
    public record LeaveRequest(String person, LocalDate start, LocalDate end) {
    }

    private final MockStore store;

    public MockCalendarController(MockStore store) {
        this.store = store;
    }

    @GetMapping("/team")
    public List<Person> team(@RequestParam String project) {
        return store.dataset().team().getOrDefault(project.toUpperCase(), List.of());
    }

    /** Leaves and holidays overlapping {@code from..to}, both inclusive. */
    @GetMapping("/calendar/events")
    public Map<String, List<Absence>> events(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Absence> events = store.dataset().absences().stream()
                .filter(a -> !a.end().isBefore(from) && !a.start().isAfter(to))
                .sorted((a, b) -> a.start().compareTo(b.start()))
                .toList();
        return Map.of("events", events);
    }

    @PostMapping("/calendar/events")
    public ResponseEntity<Absence> addLeave(@RequestBody LeaveRequest request) {
        if (request.person() == null || request.start() == null || request.end() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "person, start and end are required");
        }
        if (request.end().isBefore(request.start())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must not be before start");
        }
        Person person = store.person(request.person()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown person: " + request.person()));
        Absence leave = store.addLeave(person, request.start(), request.end());
        return ResponseEntity.created(URI.create("/mock/calendar/events/" + leave.id())).body(leave);
    }

    @DeleteMapping("/calendar/events/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!store.removeAbsence(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No event " + id);
        }
        return ResponseEntity.noContent().build();
    }
}
