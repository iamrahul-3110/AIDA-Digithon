package com.aida.contract;
import java.time.LocalDate;

public record Absence(
    String id,
    String person,
    String country,
    LocalDate start,
    LocalDate end,
    String type
) {}
