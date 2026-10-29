package com.aida.contract;
import java.time.LocalDate;
import java.util.List;

public record RiskReport(
    String project,
    LocalDate asOf,
    String bottleneckStage,
    List<Risk> items
) {}
