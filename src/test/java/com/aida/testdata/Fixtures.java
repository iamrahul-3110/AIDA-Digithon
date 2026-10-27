package com.aida.testdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Loads the shared JSON fixtures in src/test/resources/fixtures. */
public final class Fixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Fixtures() {
    }

    public static List<Issue> issues() {
        return load("sample-issues.json", new TypeReference<>() {
        });
    }

    public static List<Person> team() {
        return load("sample-team.json", new TypeReference<>() {
        });
    }

    public static List<Absence> absences() {
        return load("sample-absences.json", new TypeReference<>() {
        });
    }

    private static <T> T load(String name, TypeReference<T> type) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            return MAPPER.readValue(Objects.requireNonNull(in, name), type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
