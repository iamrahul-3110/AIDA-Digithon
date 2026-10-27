package com.aida.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

/** Every contract record survives a JSON round trip with camelCase names and ISO dates. */
class ContractJsonTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 27);

    private final JsonMapper mapper = JsonMapper.builder().build();

    static Stream<Object> samples() {
        StatusChange change = new StatusChange("In Progress", Instant.parse("2026-10-14T10:00:00Z"));
        Person person = new Person("Arjun", "IN");
        Driver driver = new Driver("leave", 3.5, "Arjun is on leave for 5 days.", List.of("SHOP-102"));
        QualityIssue qualityIssue = new QualityIssue("missing_estimate", 1, List.of("SHOP-104"), "1 of 8 stories has no estimate.");
        Risk risk = new Risk("SHOP-106", "Gift card admin report", "client_wait", "client", 8, "Min-jun");
        return Stream.of(
                change,
                new Issue("SHOP-102", "SHOP", "Story", "Redeem gift card at checkout", "In Progress",
                        List.of(new StatusChange("To Do", Instant.parse("2026-09-05T09:00:00Z")), change),
                        LocalDate.of(2026, 9, 5), null, null, "SHOP-E7", "Gift cards", "3.2", "Arjun"),
                person,
                new Absence("lv-1", "Arjun", "IN", D, D.plusDays(4), "leave"),
                new Absence("hol-1", null, "KR", D, D, "holiday"),
                new Release("SHOP", "3.2", D.plusDays(25), false),
                new Forecast("SHOP", "3.2", D, 35, D.plusDays(20), D.plusDays(39), D.plusDays(50),
                        D.plusDays(25), 0.45, 12, D.minusWeeks(12), D, 0.93, 10_000, 42L),
                driver,
                new ChangeExplanation("SHOP", "3.2", D.minusDays(7), D, D.plusDays(32), D.plusDays(39), 7, List.of(driver)),
                new WhatIf(List.of("SHOP-E7"), List.of(person), List.of("Linh"), D.plusDays(25)),
                qualityIssue,
                new DataQuality("OPS", 42, List.of(qualityIssue), false),
                risk,
                new RiskReport("SHOP", D, "Waiting for Client UAT", List.of(risk)));
    }

    @ParameterizedTest
    @MethodSource("samples")
    void roundTrips(Object record) {
        String json = mapper.writeValueAsString(record);
        Object back = mapper.readValue(json, record.getClass());
        assertThat(back).isEqualTo(record);
    }

    @Test
    void writesIsoDatesNotTimestamps() {
        String json = mapper.writeValueAsString(new Absence("lv-1", "Arjun", "IN", D, D.plusDays(4), "leave"));
        assertThat(json).contains("\"start\":\"2026-10-27\"", "\"end\":\"2026-10-31\"");

        String change = mapper.writeValueAsString(new StatusChange("QA", Instant.parse("2026-10-20T09:00:00Z")));
        assertThat(change).contains("\"at\":\"2026-10-20T09:00:00Z\"");
    }

    @Test
    void usesContractFieldNames() {
        String json = mapper.writeValueAsString(new DataQuality("OPS", 42, List.of(), false));
        assertThat(json).isEqualTo("{\"project\":\"OPS\",\"score\":42,\"issues\":[],\"forecastAllowed\":false}");
    }
}
