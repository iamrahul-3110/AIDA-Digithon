package com.aida.contract;

import java.time.LocalDate;

/**
 * Monte Carlo delivery forecast for one release.
 *
 * @param pByTarget share of runs finishing on or before {@code targetDate}, 0..1; null without a target
 */
public record Forecast(
        String project,
        String release,
        LocalDate asOf,
        int remainingItems,
        LocalDate p50,
        LocalDate p85,
        LocalDate p95,
        LocalDate targetDate,
        Double pByTarget,
        int sampleWeeks,
        LocalDate windowStart,
        LocalDate windowEnd,
        double capacityFactor,
        int runs,
        long seed) {
}
