package com.aida.contract;

import java.time.LocalDate;
import java.util.List;

/**
 * Why the p85 date moved between two dates.
 *
 * @param drivers ordered by |impactDays| descending; impactDays sum to {@code shiftDays}
 */
public record ChangeExplanation(
        String project,
        String release,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate p85From,
        LocalDate p85To,
        int shiftDays,
        List<Driver> drivers) {
}
