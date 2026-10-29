package com.aida.engine;

import com.aida.contract.Absence;
import com.aida.contract.Person;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Computes leave/holiday-aware weekly capacity ratios.
 * Returns double[] where 1.0 = full team available.
 */
public class Capacity {

    private Capacity() {}

    /**
     * @param team       list of persons on the team
     * @param absences   all absences/holidays in scope
     * @param start      Monday of first week
     * @param weeks      number of weeks to compute
     * @return array of length {@code weeks}, each entry in [0,1]
     */
    public static double[] byWeek(List<Person> team, List<Absence> absences, LocalDate start, int weeks) {
        double[] result = new double[weeks];
        int teamSize = team.size();
        if (teamSize == 0) {
            return result;
        }
        for (int w = 0; w < weeks; w++) {
            LocalDate weekStart = start.plusWeeks(w);
            LocalDate weekEnd = weekStart.plusDays(4); // Mon–Fri
            double absentDays = 0;
            for (Person person : team) {
                for (Absence abs : absences) {
                    // person-specific absence OR country-level holiday matching person's country
                    boolean personMatch = person.name().equals(abs.person());
                    boolean holidayMatch = abs.person() == null && person.country().equals(abs.country());
                    if (!personMatch && !holidayMatch) continue;
                    // overlap with [weekStart, weekEnd]
                    LocalDate overlapStart = abs.start().isBefore(weekStart) ? weekStart : abs.start();
                    LocalDate overlapEnd = abs.end().isAfter(weekEnd) ? weekEnd : abs.end();
                    if (!overlapStart.isAfter(overlapEnd)) {
                        // count only weekdays
                        LocalDate d = overlapStart;
                        while (!d.isAfter(overlapEnd)) {
                            DayOfWeek dow = d.getDayOfWeek();
                            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                                absentDays++;
                            }
                            d = d.plusDays(1);
                        }
                    }
                }
            }
            double totalDays = teamSize * 5.0;
            double available = totalDays - absentDays;
            result[w] = Math.max(0, Math.min(1.0, available / totalDays));
        }
        return result;
    }
}
