package com.aida.contract;
import java.util.List;

public record DataQuality(
    String project,
    int score,
    List<QualityIssue> issues,
    boolean forecastAllowed
) {}
