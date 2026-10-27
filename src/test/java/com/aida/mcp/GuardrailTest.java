package com.aida.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

/** Extraction, normalisation and matching of draft values against a token's recent tool results. */
class GuardrailTest {

    /** A clock tests can move forward. */
    static final class MovableClock extends Clock {
        Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TempDir
    Path dir;

    MovableClock clock;
    AuditLog auditLog;
    Guardrail guardrail;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-10-27T10:00:00Z"));
        auditLog = new AuditLog(JsonMapper.builder().build(), dir, clock);
        guardrail = new Guardrail(auditLog, clock);
    }

    static Map<String, Object> forecast() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("project", "SHOP");
        f.put("release", "3.2");
        f.put("asOf", "2026-10-27");
        f.put("remainingItems", 18);
        f.put("p50", "2026-11-21");
        f.put("p85", "2026-11-24");
        f.put("p95", "2026-11-25");
        f.put("targetDate", "2026-11-21");
        f.put("pByTarget", 0.6282);
        f.put("sampleWeeks", 12);
        f.put("capacityFactor", 0.953);
        f.put("runs", 10000);
        f.put("meta", Map.of("tool", "forecast_delivery", "calledAt", "2026-10-27T09:59:00Z"));
        return f;
    }

    Guardrail.Verification check(String draft) {
        return guardrail.verifyAnswer("lana", draft);
    }

    @Test
    void draftBuiltFromTheResultPasses() {
        auditLog.remember("lana", "forecast_delivery", forecast());
        Guardrail.Verification v = check("""
                SHOP 3.2: 63% chance to ship by Nov 21. p85 is November 24, 2026 and p95 2026-11-25; 85% of runs
                finish by Nov 24. 18 items remain. Based on 10,000 runs over 12 weeks; capacity is at 95.3%.""");
        assertThat(v.unmatched()).isEmpty();
        assertThat(v.ok()).isTrue();
    }

    @Test
    void inventedPercentageIsFlagged() {
        auditLog.remember("lana", "forecast_delivery", forecast());
        Guardrail.Verification v = check("There is a 91% chance to ship by Nov 21.");
        assertThat(v.ok()).isFalse();
        assertThat(v.unmatched()).containsExactly("91%");
    }

    @Test
    void inventedNumbersAndDatesAreFlagged() {
        auditLog.remember("lana", "forecast_delivery", forecast());
        assertThat(check("About 25 items remain; expect it by Dec 3 or 2026-12-10.").unmatched())
                .containsExactlyInAnyOrder("25", "Dec 3", "2026-12-10");
    }

    @Test
    void percentagesNormalise() {
        auditLog.remember("lana", "forecast_delivery", Map.of("pByTarget", 0.45, "share", 30));
        assertThat(check("45% by target, or 45 percent; 0.45 as a fraction; 30% stale.").ok()).isTrue();
        assertThat(check("46% by target").unmatched()).containsExactly("46%");
        // Rounding is judged at the precision the draft shows.
        assertThat(check("45.0% by target").ok()).isTrue();
        assertThat(check("45.4% by target").unmatched()).containsExactly("45.4%");
    }

    @Test
    void datesNormaliseInAnyCommonForm() {
        auditLog.remember("lana", "forecast_delivery", Map.of("p85", "2026-12-15"));
        assertThat(check("Dec 15, 15 December, December 15th, 15 Dec 2026, Dec. 15 and 2026-12-15.").ok()).isTrue();
        assertThat(check("December 15, 2027").unmatched()).containsExactly("December 15, 2027");
        assertThat(check("Dec 16").unmatched()).containsExactly("Dec 16");
    }

    @Test
    void dateWithoutYearMayBeNextYear() {
        clock.now = Instant.parse("2026-12-20T10:00:00Z");
        auditLog.remember("lana", "forecast_delivery", Map.of("p85", "2027-01-05"));
        assertThat(check("Likely by Jan 5.").ok()).isTrue();
    }

    @Test
    void issueKeysLabelsAndYearsAreNotInventedNumbers() {
        auditLog.remember("lana", "check_data_quality", Map.of("score", 99, "issues", List.of(Map.of(
                "type", "missing_estimate", "count", 2, "keys", List.of("SHOP-21", "SHOP-7"),
                "detail", "2 of 104 stories (2%) have no estimate."))));
        auditLog.remember("lana", "forecast_delivery", forecast());
        Guardrail.Verification v = check("Score 99. SHOP-21 and SHOP-7 (2 of 104 stories, 2%) lack estimates; "
                + "the p85 date is in 2026.");
        assertThat(v.unmatched()).isEmpty();
    }

    @Test
    void numbersInResultSentencesCount() {
        auditLog.remember("lana", "check_data_quality", Map.of("issues", List.of(Map.of(
                "detail", "9 of 30 active items (30%) have not changed status in 14+ days."))));
        assertThat(check("9 of 30 active items (30%) have been idle for 14 days or more.").ok()).isTrue();
    }

    @Test
    void anotherTokensResultsDoNotCount() {
        auditLog.remember("someone-else", "forecast_delivery", forecast());
        Guardrail.Verification v = check("63% chance to ship by Nov 21.");
        assertThat(v.ok()).isFalse();
        assertThat(v.unmatched()).containsExactly("63%", "Nov 21");
    }

    @Test
    void resultsOlderThanThirtyMinutesDoNotCount() {
        auditLog.remember("lana", "forecast_delivery", forecast());
        clock.now = clock.now.plus(Duration.ofMinutes(29));
        assertThat(check("63% by Nov 21").ok()).isTrue();
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertThat(check("63% by Nov 21").unmatched()).containsExactly("63%", "Nov 21");
    }

    @Test
    void metaBlockIsNotASource() {
        auditLog.remember("lana", "forecast_delivery", Map.of("meta", Map.of("calledAt", "2026-10-27T09:59:00Z",
                "windowStart", "2026-08-03")));
        assertThat(check("Window starts 2026-08-03.").unmatched()).containsExactly("2026-08-03");
    }

    @Test
    void draftWithoutValuesPasses() {
        assertThat(check("The release looks at risk; consider a what-if.").ok()).isTrue();
        assertThat(check("").ok()).isTrue();
    }
}
