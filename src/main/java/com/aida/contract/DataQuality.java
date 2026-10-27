package com.aida.contract;

import java.util.List;

/**
 * @param score 0..100
 */
public record DataQuality(String project, int score, List<QualityIssue> issues, boolean forecastAllowed) {
}
