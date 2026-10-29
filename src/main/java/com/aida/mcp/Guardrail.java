package com.aida.mcp;

import java.util.*;
import java.util.regex.*;

/**
 * Guardrail: extracts numbers/dates from a draft and checks them against audit entries.
 */
public class Guardrail {

    private Guardrail() {}

    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}-\\d{2}-\\d{2}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\w*\\.?\\s+\\d{1,2},?\\s*\\d{0,4})",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b");

    public static List<String> verify(String draft, List<Map<String, Object>> recentEntries) {
        List<String> unmatched = new ArrayList<>();
        if (recentEntries.isEmpty()) return unmatched;

        // Collect all values from audit resultSummary strings
        Set<String> knownValues = new HashSet<>();
        for (Map<String, Object> entry : recentEntries) {
            String summary = String.valueOf(entry.getOrDefault("resultSummary", ""));
            extractNumbers(summary).forEach(knownValues::add);
        }

        // Extract percentages from draft, normalise to 0..1
        Matcher pm = PERCENT_PATTERN.matcher(draft);
        while (pm.find()) {
            double pct = Double.parseDouble(pm.group(1)) / 100.0;
            String rounded = String.format("%.2f", pct);
            if (knownValues.stream().noneMatch(v -> v.contains(rounded) || roughMatch(v, rounded))) {
                unmatched.add("percentage: " + pm.group(0));
            }
        }

        return unmatched;
    }

    private static Set<String> extractNumbers(String text) {
        Set<String> nums = new HashSet<>();
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) nums.add(m.group(1));
        return nums;
    }

    private static boolean roughMatch(String known, String check) {
        try {
            double a = Double.parseDouble(known);
            double b = Double.parseDouble(check);
            return Math.abs(a - b) < 0.05;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
