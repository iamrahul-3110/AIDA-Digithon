package com.aida.engine;

import com.aida.contract.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ForecastEngineTest {

    private static List<Issue> makeIssues(int done, int remaining, String release, LocalDate base) {
        var list = new java.util.ArrayList<Issue>();
        for (int i = 0; i < done; i++) {
            LocalDate resolved = base.minusWeeks(1).minusDays(i % 7);
            list.add(new Issue("SHOP-" + i, "SHOP", "Story", "Done " + i, "Done",
                List.of(
                    new StatusChange("To Do", base.minusWeeks(14).atStartOfDay().toInstant(ZoneOffset.UTC)),
                    new StatusChange("Done", resolved.atStartOfDay().toInstant(ZoneOffset.UTC))
                ), base.minusWeeks(14), resolved, 3.0, null, null, release, "Arjun"));
        }
        for (int i = 0; i < remaining; i++) {
            list.add(new Issue("SHOP-" + (done + i), "SHOP", "Story", "Rem " + i, "In Progress",
                List.of(new StatusChange("To Do", base.minusWeeks(2).atStartOfDay().toInstant(ZoneOffset.UTC))),
                base.minusWeeks(2), null, 3.0, null, null, release, "Arjun"));
        }
        return list;
    }

    @Test void sameInputSameOutput() {
        var asOf = LocalDate.of(2026, 10, 1);
        var issues = makeIssues(48, 35, "3.2", asOf);
        var team = List.of(new Person("Arjun","IN"), new Person("Priya","IN"));
        var f1 = ForecastEngine.forecast(issues, "3.2", asOf, team, List.of(), null, 10000, 42);
        var f2 = ForecastEngine.forecast(issues, "3.2", asOf, team, List.of(), null, 10000, 42);
        assertThat(f1.p85()).isEqualTo(f2.p85());
    }

    @Test void moreLeaveLaterOrEqualP85() {
        var asOf = LocalDate.of(2026, 10, 1);
        var issues = makeIssues(48, 35, "3.2", asOf);
        var team = List.of(new Person("Arjun","IN"), new Person("Priya","IN"));
        var fNo = ForecastEngine.forecast(issues, "3.2", asOf, team, List.of(), null, 10000, 42);
        var leave = List.of(new Absence("lv-1","Arjun","IN", asOf.plusDays(5), asOf.plusDays(12), "leave"));
        var fWith = ForecastEngine.forecast(issues, "3.2", asOf, team, leave, null, 10000, 42);
        assertThat(fWith.p85()).isAfterOrEqualTo(fNo.p85());
    }

    @Test void earlierTargetLowerOrEqualPByTarget() {
        var asOf = LocalDate.of(2026, 10, 1);
        var issues = makeIssues(48, 35, "3.2", asOf);
        var team = List.of(new Person("Arjun","IN"), new Person("Priya","IN"));
        var fE = ForecastEngine.forecast(issues, "3.2", asOf, team, List.of(), asOf.plusDays(10), 10000, 42);
        var fL = ForecastEngine.forecast(issues, "3.2", asOf, team, List.of(), asOf.plusDays(40), 10000, 42);
        assertThat(fE.pByTarget()).isLessThanOrEqualTo(fL.pByTarget());
    }

    @Test void tenKRunsUnderOneSecond() {
        var asOf = LocalDate.of(2026, 10, 1);
        var issues = makeIssues(48, 35, "3.2", asOf);
        var team = List.of(new Person("Arjun","IN"), new Person("Priya","IN"));
        long t0 = System.currentTimeMillis();
        ForecastEngine.forecast(issues, "3.2", asOf, team, List.of(), null, 10000, 42);
        assertThat(System.currentTimeMillis() - t0).isLessThan(1000L);
    }
}
