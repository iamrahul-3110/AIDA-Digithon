package com.aida.contract;

import java.time.LocalDate;

/**
 * A leave or a public holiday.
 *
 * @param person null for a public holiday, which applies to everyone in {@code country}
 * @param end    inclusive
 * @param type   leave or holiday
 */
public record Absence(String id, String person, String country, LocalDate start, LocalDate end, String type) {
}
