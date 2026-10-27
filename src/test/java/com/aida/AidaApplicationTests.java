package com.aida;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.aida.contract.Release;
import com.aida.contract.StatusChange;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class AidaApplicationTests {

    @Autowired
    JsonMapper mapper;

    @Test
    void contextLoads() {
    }

    @Test
    void applicationMapperWritesIsoDates() {
        assertThat(mapper.writeValueAsString(new Release("SHOP", "3.2", LocalDate.of(2026, 12, 15), false)))
                .contains("\"releaseDate\":\"2026-12-15\"");
        assertThat(mapper.writeValueAsString(new StatusChange("QA", Instant.parse("2026-10-20T09:00:00Z"))))
                .contains("\"at\":\"2026-10-20T09:00:00Z\"");
    }
}
