package com.aida.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.Person;

/** Pure: no Spring, no I/O. Signature FROZEN; implemented by Person B in M3 (B2). */
public final class Capacity {

    private Capacity() {
    }

    /**
     * Available share of the team per week from {@code start}; 1.0 = full team.
     *
     * <p>Week i covers the 7 days from {@code start + 7i}. Its value is the available person-days divided by
     * team size x 5, counting only weekdays and skipping each person's leave and their country's public holidays
     * (a day off counts once even when a leave and a holiday overlap). With no team known there is nothing to
     * adjust, so every week is 1.0.
     */
    public static double[] byWeek(List<Person> team, List<Absence> absences, LocalDate start, int weeks) {
        double[] out = new double[weeks];
        if (team == null || team.isEmpty()) {
            Arrays.fill(out, 1.0);
            return out;
        }
        List<Absence> all = absences == null ? List.of() : absences;
        for (int w = 0; w < weeks; w++) {
            int available = 0;
            for (Person person : team) {
                List<Absence> own = all.stream().filter(a -> appliesTo(a, person)).toList();
                for (int d = 0; d < 7; d++) {
                    LocalDate day = start.plusDays(7L * w + d);
                    if (isWeekday(day) && own.stream().noneMatch(a -> covers(a, day))) {
                        available++;
                    }
                }
            }
            out[w] = available / (team.size() * 5.0);
        }
        return out;
    }

    /** Person-days of leave and holiday falling on weekdays in {@code from..to} (inclusive) for this team. */
    static int absentPersonDays(List<Person> team, List<Absence> absences, LocalDate from, LocalDate to) {
        int days = 0;
        for (Person person : team) {
            List<Absence> own = absences.stream().filter(a -> appliesTo(a, person)).toList();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                LocalDate d = day;
                if (isWeekday(d) && own.stream().anyMatch(a -> covers(a, d))) {
                    days++;
                }
            }
        }
        return days;
    }

    static boolean appliesTo(Absence a, Person person) {
        return a.person() == null ? person.country() != null && person.country().equals(a.country())
                : a.person().equals(person.name());
    }

    private static boolean covers(Absence a, LocalDate day) {
        return !day.isBefore(a.start()) && !day.isAfter(a.end());
    }

    private static boolean isWeekday(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
