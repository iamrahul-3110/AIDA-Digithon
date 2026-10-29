package com.aida.contract;
import java.time.LocalDate;

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
    long seed
) {}
