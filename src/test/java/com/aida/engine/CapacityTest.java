package com.aida.engine;

import static com.aida.testdata.TestData.holiday;
import static com.aida.testdata.TestData.leave;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aida.contract.Person;
import com.aida.testdata.TestData;

class CapacityTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    static final List<Person> TWO = List.of(new Person("Arjun", "IN"), new Person("Linh", "VN"));

    @Test
    void fullTeamWithoutAbsencesIsOne() {
        assertThat(Capacity.byWeek(TWO, List.of(), MONDAY, 3)).containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    void anyStartDayCoversFiveWeekdaysPerWeek() {
        assertThat(Capacity.byWeek(TWO, List.of(), MONDAY.plusDays(3), 2)).containsExactly(1.0, 1.0);
    }

    @Test
    void holidayOnlyHitsItsCountry() {
        double[] c = Capacity.byWeek(TWO, List.of(holiday("IN", MONDAY.plusDays(2))), MONDAY, 2);

        assertThat(c).containsExactly(0.9, 1.0);
    }

    @Test
    void leaveHitsOnlyThatPersonAndSkipsWeekends() {
        // Thursday to the following Tuesday: 4 weekdays, 2 in each week.
        double[] c = Capacity.byWeek(TWO, List.of(leave("lv", "Linh", "VN", MONDAY.plusDays(3), MONDAY.plusDays(8))),
                MONDAY, 2);

        assertThat(c).containsExactly(0.8, 0.8);
    }

    @Test
    void overlappingLeaveAndHolidayCountOnce() {
        double[] c = Capacity.byWeek(TWO, List.of(
                leave("lv", "Arjun", "IN", MONDAY, MONDAY.plusDays(4)), holiday("IN", MONDAY.plusDays(1))), MONDAY, 1);

        assertThat(c).containsExactly(0.5);
    }

    @Test
    void shopTeamWithFixtureAbsences() {
        double[] c = Capacity.byWeek(TestData.SHOP_TEAM, com.aida.testdata.Fixtures.absences(),
                LocalDate.of(2026, 11, 16), 1);

        // Arjun on leave all week: 25 of 30 person-days.
        assertThat(c[0]).isEqualTo(25 / 30.0);
    }
}
