package com.aida.mcp;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Checks that every number, percentage and date in a draft answer comes from AIDA tool results the same token
 * received in the last 30 minutes ({@link AuditLog#recentResults}).
 *
 * <p>Normalisation: "45%" and "45 percent" match 0.45 (or 45); "10,000" is 10000; "Dec 15", "15 December" and
 * "December 15, 2026" are dates, without a year meaning this year or next. A value matches when it equals a result
 * value at the precision the draft shows ("63%" matches 0.6282; "95.3%" matches 0.953). Issue keys (SHOP-21) and
 * labels (p85) are not values. Digits in result field names (p50, p85, p95) count as values, so "85% of runs" passes;
 * so does the year of any result date.
 */
@Component
public class Guardrail {

    public static final String TOOL = "verify_answer";

    /** {"ok": bool, "unmatched": [...]}: unmatched lists the draft's text for each value with no source. */
    public record Verification(boolean ok, List<String> unmatched) {
    }

    private static final String MONTH = "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|(?-i:May)|June?|July?"
            + "|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)";
    private static final Pattern ISO_DATE = Pattern.compile("(?<![\\d-])(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b" + MONTH + "\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4})\\b)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+" + MONTH + "\\b\\.?(?:,?\\s+(\\d{4})\\b)?", Pattern.CASE_INSENSITIVE);
    /** A number not glued to letters, digits or a dash on its left (so not SHOP-21 or p85), optionally a percentage. */
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d.,\\-])(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?(?![\\p{L}\\d])(\\s*%|\\s+percent\\b)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final double EPSILON = 1e-9;

    private final AuditLog auditLog;
    private final Clock clock;

    public Guardrail(AuditLog auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public Verification verifyAnswer(String tokenName, String draft) {
        Set<Double> numbers = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (AuditLog.Entry entry : auditLog.recentResults(tokenName)) {
            entry.result().forEach((key, value) -> {
                if (!key.equals("meta")) {
                    collect(key, value, numbers, dates);
                }
            });
        }
        dates.forEach(d -> numbers.add((double) d.getYear()));

        if (draft == null || draft.isBlank()) {
            return new Verification(true, List.of());
        }
        // Unmatched values keyed by their position in the draft, so they are reported in reading order.
        TreeMap<Integer, String> unmatched = new TreeMap<>();
        int thisYear = LocalDate.now(clock).getYear();
        StringBuilder rest = new StringBuilder(draft);

        Matcher iso = ISO_DATE.matcher(draft);
        while (iso.find()) {
            LocalDate date = date(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
            if (date == null || !dates.contains(date)) {
                unmatched.put(iso.start(), iso.group());
            }
            mask(rest, iso.start(), iso.end());
        }
        matchNamedDates(MONTH_DAY.matcher(rest.toString()), 1, 2, rest, dates, thisYear, unmatched);
        matchNamedDates(DAY_MONTH.matcher(rest.toString()), 2, 1, rest, dates, thisYear, unmatched);

        Matcher number = NUMBER.matcher(rest.toString());
        while (number.find()) {
            double value = parse(number.group(1), number.group(2));
            double tolerance = tolerance(number.group(2));
            boolean percent = number.group(3) != null;
            boolean found = numbers.stream().anyMatch(v -> Math.abs(value - v) <= tolerance + EPSILON
                    || (percent && Math.abs(value - v * 100) <= tolerance + EPSILON));
            if (!found) {
                unmatched.put(number.start(), number.group().trim());
            }
        }
        return new Verification(unmatched.isEmpty(), List.copyOf(unmatched.values()));
    }

    private static void matchNamedDates(Matcher m, int monthGroup, int dayGroup, StringBuilder rest,
            Set<LocalDate> dates, int thisYear, Map<Integer, String> unmatched) {
        while (m.find()) {
            int month = month(m.group(monthGroup));
            int day = Integer.parseInt(m.group(dayGroup));
            String year = m.group(3);
            List<LocalDate> candidates = new ArrayList<>();
            if (year != null) {
                candidates.add(date(Integer.parseInt(year), month, day));
            } else {
                candidates.add(date(thisYear, month, day));
                candidates.add(date(thisYear + 1, month, day));
            }
            if (candidates.stream().noneMatch(d -> d != null && dates.contains(d))) {
                unmatched.put(m.start(), m.group().trim());
            }
            mask(rest, m.start(), m.end());
        }
    }

    /** Every value in a tool result: numbers, numbers and ISO dates inside strings, and digits in field names. */
    private static void collect(String key, Object value, Set<Double> numbers, Set<LocalDate> dates) {
        if (key != null) {
            Matcher digits = DIGITS.matcher(key);
            while (digits.find()) {
                numbers.add(Double.parseDouble(digits.group()));
            }
        }
        switch (value) {
            case null -> {
            }
            case Number n -> numbers.add(n.doubleValue());
            case Map<?, ?> map -> map.forEach((k, v) -> collect(String.valueOf(k), v, numbers, dates));
            case List<?> list -> list.forEach(v -> collect(null, v, numbers, dates));
            case String s -> collectText(s, numbers, dates);
            default -> {
            }
        }
    }

    private static void collectText(String text, Set<Double> numbers, Set<LocalDate> dates) {
        StringBuilder rest = new StringBuilder(text);
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            LocalDate date = date(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
            if (date != null) {
                dates.add(date);
            }
            mask(rest, iso.start(), iso.end());
        }
        Matcher number = NUMBER.matcher(rest.toString());
        while (number.find()) {
            double value = parse(number.group(1), number.group(2));
            numbers.add(value);
            if (number.group(3) != null) {
                numbers.add(value / 100);
            }
        }
    }

    private static double parse(String whole, String fraction) {
        return Double.parseDouble(whole.replace(",", "") + (fraction == null ? "" : fraction));
    }

    /** Half a unit of the last digit the draft shows: "63" → 0.5, "95.3" → 0.05. */
    private static double tolerance(String fraction) {
        int decimals = fraction == null ? 0 : fraction.length() - 1;
        return 0.5 * Math.pow(10, -decimals);
    }

    private static int month(String name) {
        return switch (name.substring(0, 3).toLowerCase(Locale.ROOT)) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            default -> 12;
        };
    }

    private static LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static void mask(StringBuilder text, int start, int end) {
        for (int i = start; i < end; i++) {
            text.setCharAt(i, ' ');
        }
    }
}
