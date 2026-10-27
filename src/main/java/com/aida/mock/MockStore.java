package com.aida.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.aida.contract.Absence;
import com.aida.contract.Person;

import tools.jackson.databind.json.JsonMapper;

/**
 * The demo data behind the mock endpoints. Generated on startup and on reset, kept in memory, and saved to
 * {@code <aida.data-dir>/mock-data.json} after every change so the current state can be inspected.
 */
@Component
@Profile("mock")
public class MockStore {

    private final JsonMapper mapper;
    private final Path file;
    private final long seed;
    private final String fixedAsOf;
    private Dataset dataset;
    private LocalDate asOf;
    private int nextLeaveId;

    public MockStore(JsonMapper mapper, @Value("${aida.data-dir}") Path dataDir,
            @Value("${aida.mock.seed:7}") long seed, @Value("${aida.mock.as-of:}") String fixedAsOf) {
        this.mapper = mapper;
        this.file = dataDir.resolve("mock-data.json");
        this.seed = seed;
        this.fixedAsOf = fixedAsOf;
        reset();
    }

    /** Regenerates the demo data for today (or {@code aida.mock.as-of}), dropping leaves added since. */
    public synchronized Dataset reset() {
        asOf = fixedAsOf.isBlank() ? LocalDate.now(DataGenerator.ZONE) : LocalDate.parse(fixedAsOf);
        dataset = DataGenerator.generate(asOf, seed);
        nextLeaveId = dataset.absences().size() + 1;
        save();
        return dataset;
    }

    public synchronized Dataset dataset() {
        return dataset;
    }

    public synchronized LocalDate asOf() {
        return asOf;
    }

    public long seed() {
        return seed;
    }

    public synchronized Optional<Person> person(String name) {
        return dataset.team().values().stream().flatMap(List::stream).filter(p -> p.name().equals(name)).findFirst();
    }

    /** Adds a leave; the person must be on a team, and the country comes from the team. */
    public synchronized Absence addLeave(Person person, LocalDate start, LocalDate end) {
        String id;
        do {
            id = "lv-" + nextLeaveId++;
        } while (exists(id));
        Absence leave = new Absence(id, person.name(), person.country(), start, end, "leave");
        List<Absence> absences = new ArrayList<>(dataset.absences());
        absences.add(leave);
        replaceAbsences(absences);
        return leave;
    }

    public synchronized boolean removeAbsence(String id) {
        List<Absence> absences = new ArrayList<>(dataset.absences());
        boolean removed = absences.removeIf(a -> a.id().equals(id));
        if (removed) {
            replaceAbsences(absences);
        }
        return removed;
    }

    private boolean exists(String id) {
        return dataset.absences().stream().anyMatch(a -> a.id().equals(id));
    }

    private void replaceAbsences(List<Absence> absences) {
        dataset = new Dataset(dataset.issues(), dataset.versions(), dataset.team(), List.copyOf(absences));
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), dataset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
