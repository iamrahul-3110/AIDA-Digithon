package com.aida.alerts;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** "Today" pinned to the mock's demo date (aida.mock.as-of in the test properties), whatever day the tests run. */
@TestConfiguration(proxyBeanMethods = false)
public class DemoClock {

    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata"));

    @Bean
    @Primary
    Clock demoClock() {
        return Clock.fixed(NOW.toInstant(), NOW.getZone());
    }
}
