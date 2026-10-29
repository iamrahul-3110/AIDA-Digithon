package com.aida.contract;
import java.time.LocalDate;
import java.util.List;

public record ChangeExplanation(
    String project,
    String release,
    LocalDate fromDate,
    LocalDate toDate,
    LocalDate p85From,
    LocalDate p85To,
    int shiftDays,
    List<Driver> drivers
) {}
