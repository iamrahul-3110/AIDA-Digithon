package com.aida.engine;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/** Date conventions shared by the engines. Pure. */
final class EngineDates {

    /** Status-change instants are turned into calendar days in UTC, the zone the contract instants use. */
    static final ZoneId ZONE = ZoneOffset.UTC;

    /** Throughput is sampled over this many ISO weeks before asOf's week. */
    static final int SAMPLE_WEEKS = 12;

    private EngineDates() {
    }

    static LocalDate day(Instant at) {
        return LocalDate.ofInstant(at, ZONE);
    }

    /** First instant after the end of day {@code d}. */
    static Instant endOfDay(LocalDate d) {
        return d.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Monday of the first of the 12 full ISO weeks before asOf's week. */
    static LocalDate windowStart(LocalDate asOf) {
        return monday(asOf).minusWeeks(SAMPLE_WEEKS);
    }

    /** Sunday before asOf's week: the last day of the throughput window. */
    static LocalDate windowEnd(LocalDate asOf) {
        return monday(asOf).minusDays(1);
    }
}
