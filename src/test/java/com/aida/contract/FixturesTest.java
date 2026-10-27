package com.aida.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Guards the shape of the shared test fixtures that later engine tests rely on. */
class FixturesTest {

    /** The date the fixtures are written against. */
    static final Instant AS_OF = Instant.parse("2026-10-27T09:00:00Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static <T> T load(String name, TypeReference<T> type) {
        try (InputStream in = FixturesTest.class.getResourceAsStream("/fixtures/" + name)) {
            return MAPPER.readValue(Objects.requireNonNull(in, name), type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private final List<Issue> issues = load("sample-issues.json", new TypeReference<>() {
    });

    @Test
    void fifteenShopIssuesCoveringEveryStatus() {
        assertThat(issues).hasSize(15).allMatch(i -> i.project().equals("SHOP"));
        assertThat(issues).extracting(Issue::status).containsAll(Statuses.WORKFLOW);
    }

    @Test
    void twoEpics() {
        assertThat(issues.stream().map(Issue::epic).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("SHOP-E5", "SHOP-E7");
    }

    @Test
    void exactlyOneMissingEstimate() {
        assertThat(issues).filteredOn(i -> i.estimate() == null).extracting(Issue::key).containsExactly("SHOP-104");
    }

    @Test
    void oneItemWaitingForClientUatForEightDays() {
        List<Issue> waiting = issues.stream().filter(i -> Statuses.isClientSide(i.status())).toList();
        assertThat(waiting).hasSize(1);
        StatusChange entered = waiting.getFirst().statusHistory().getLast();
        assertThat(Duration.between(entered.at(), AS_OF).toDays()).isEqualTo(8);
    }

    @Test
    void historiesStartWithToDoAtCreationAndEndAtCurrentStatus() {
        for (Issue issue : issues) {
            StatusChange first = issue.statusHistory().getFirst();
            assertThat(first.status()).as(issue.key()).isEqualTo(Statuses.TO_DO);
            assertThat(first.at().toString()).as(issue.key()).startsWith(issue.created().toString());
            assertThat(issue.statusHistory().getLast().status()).as(issue.key()).isEqualTo(issue.status());
            assertThat(issue.resolved() != null).as(issue.key()).isEqualTo(issue.status().equals(Statuses.DONE));
        }
    }

    @Test
    void teamAndAbsences() {
        List<Person> team = load("sample-team.json", new TypeReference<>() {
        });
        List<Absence> absences = load("sample-absences.json", new TypeReference<>() {
        });
        assertThat(team).hasSize(6).extracting(Person::country).contains("IN", "KR", "VN");
        assertThat(absences).filteredOn(a -> a.type().equals("leave")).hasSize(2).allMatch(a -> a.person() != null);
        assertThat(absences).filteredOn(a -> a.type().equals("holiday")).hasSize(2).allMatch(a -> a.person() == null);
    }
}
