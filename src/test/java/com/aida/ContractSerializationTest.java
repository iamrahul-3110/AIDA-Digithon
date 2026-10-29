package com.aida;

import com.aida.contract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void statusChange_roundTrip() throws Exception {
        var sc = new StatusChange("In Progress", Instant.parse("2026-09-03T10:00:00Z"));
        var json = mapper.writeValueAsString(sc);
        var back = mapper.readValue(json, StatusChange.class);
        assertThat(back.status()).isEqualTo("In Progress");
        assertThat(back.at()).isEqualTo(sc.at());
    }

    @Test
    void issue_roundTrip() throws Exception {
        var issue = new Issue("SHOP-101", "SHOP", "Story", "Test", "In Progress",
            List.of(new StatusChange("To Do", Instant.parse("2026-09-01T00:00:00Z"))),
            LocalDate.of(2026, 9, 1), null, 3.0, "SHOP-E7", "Gift cards", "3.2", "Arjun");
        var json = mapper.writeValueAsString(issue);
        var back = mapper.readValue(json, Issue.class);
        assertThat(back.key()).isEqualTo("SHOP-101");
        assertThat(back.estimate()).isEqualTo(3.0);
        assertThat(back.statusHistory()).hasSize(1);
    }

    @Test
    void forecast_roundTrip() throws Exception {
        var f = new Forecast("SHOP", "3.2", LocalDate.now(), 35,
            LocalDate.now().plusDays(20), LocalDate.now().plusDays(28),
            LocalDate.now().plusDays(35), LocalDate.now().plusDays(25),
            0.45, 12, LocalDate.now().minusWeeks(12), LocalDate.now(),
            0.92, 10000, 42L);
        var json = mapper.writeValueAsString(f);
        var back = mapper.readValue(json, Forecast.class);
        assertThat(back.project()).isEqualTo("SHOP");
        assertThat(back.pByTarget()).isEqualTo(0.45);
    }

    @Test
    void absence_roundTrip() throws Exception {
        var a = new Absence("lv-1", "Arjun", "IN",
            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), "leave");
        var json = mapper.writeValueAsString(a);
        var back = mapper.readValue(json, Absence.class);
        assertThat(back.person()).isEqualTo("Arjun");
        assertThat(back.start()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void dataQuality_roundTrip() throws Exception {
        var dq = new DataQuality("SHOP", 72,
            List.of(new QualityIssue("stale_status", 2, List.of("SHOP-101"), "2 stale items")),
            true);
        var json = mapper.writeValueAsString(dq);
        var back = mapper.readValue(json, DataQuality.class);
        assertThat(back.score()).isEqualTo(72);
        assertThat(back.forecastAllowed()).isTrue();
    }

    @Test
    void risk_roundTrip() throws Exception {
        var r = new Risk("SHOP-101", "Test story", "aging", "team", 18, "Arjun");
        var json = mapper.writeValueAsString(r);
        var back = mapper.readValue(json, Risk.class);
        assertThat(back.days()).isEqualTo(18);
    }

    @Test
    void person_roundTrip() throws Exception {
        var p = new Person("Arjun", "IN");
        var json = mapper.writeValueAsString(p);
        var back = mapper.readValue(json, Person.class);
        assertThat(back.name()).isEqualTo("Arjun");
        assertThat(back.country()).isEqualTo("IN");
    }
}
