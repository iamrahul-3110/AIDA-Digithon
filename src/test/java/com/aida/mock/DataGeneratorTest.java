package com.aida.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Release;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;

/**
 * The generator is deterministic and plants every demo data target from the spec. Runs for every day of the week,
 * since the app generates for "today".
 */
@ParameterizedClass
@ValueSource(strings = {"2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31",
        "2026-11-01", "2026-12-07"})
class DataGeneratorTest {

    final LocalDate asOf;
    final Instant endOfAsOf;
    final Dataset data;

    DataGeneratorTest(LocalDate asOf) {
        this.asOf = asOf;
        this.endOfAsOf = asOf.plusDays(1).atStartOfDay(DataGenerator.ZONE).toInstant();
        this.data = DataGenerator.generate(asOf, 7);
    }

    List<Issue> project(String key) {
        return data.issues().stream().filter(i -> i.project().equals(key)).toList();
    }

    List<Issue> shop32() {
        return project("SHOP").stream().filter(i -> "3.2".equals(i.fixVersion())).toList();
    }

    long daysInStatus(Issue issue) {
        return Duration.between(issue.statusHistory().getLast().at(), endOfAsOf).toDays();
    }

    @Test
    void sameAsOfAndSeedGiveIdenticalData() {
        assertThat(DataGenerator.generate(asOf, 7)).isEqualTo(data);
        assertThat(DataGenerator.generate(asOf, 8).issues()).isNotEqualTo(data.issues());
    }

    @Test
    void issueHistoriesAreConsistent() {
        for (Issue issue : data.issues()) {
            List<StatusChange> history = issue.statusHistory();
            assertThat(history.getFirst().status()).as(issue.key()).isEqualTo(Statuses.TO_DO);
            assertThat(LocalDate.ofInstant(history.getFirst().at(), DataGenerator.ZONE)).as(issue.key())
                    .isEqualTo(issue.created());
            assertThat(LocalDate.ofInstant(history.getFirst().at(), ZoneOffset.UTC)).as(issue.key() + " UTC date")
                    .isEqualTo(issue.created());
            for (int i = 1; i < history.size(); i++) {
                assertThat(history.get(i).at()).as(issue.key()).isAfterOrEqualTo(history.get(i - 1).at());
                assertThat(Statuses.WORKFLOW.indexOf(history.get(i).status())).as(issue.key())
                        .isGreaterThan(Statuses.WORKFLOW.indexOf(history.get(i - 1).status()));
            }
            assertThat(history.getLast().at()).as(issue.key()).isBefore(endOfAsOf);
            assertThat(history.getLast().status()).as(issue.key()).isEqualTo(issue.status());
            assertThat(issue.resolved() != null).as(issue.key()).isEqualTo(issue.status().equals(Statuses.DONE));
            assertThat(issue.epic()).as(issue.key()).isNotNull();
            assertThat(issue.fixVersion()).as(issue.key()).isNotNull();
        }
    }

    @Test
    void keysAreUniqueAndFollowCreationOrder() {
        for (String key : List.of("SHOP", "OPS")) {
            List<Issue> issues = project(key);
            assertThat(issues).extracting(Issue::key).doesNotHaveDuplicates();
            for (int i = 1; i < issues.size(); i++) {
                assertThat(issues.get(i).statusHistory().getFirst().at())
                        .isAfterOrEqualTo(issues.get(i - 1).statusHistory().getFirst().at());
            }
        }
    }

    @Test
    void releases() {
        assertThat(data.versions()).containsExactly(
                new Release("SHOP", "3.1", data.versions().get(0).releaseDate(), true),
                new Release("SHOP", "3.2", asOf.plusDays(25), false),
                new Release("SHOP", "3.3", asOf.plusDays(25).plusWeeks(6), false),
                new Release("OPS", "Q4", asOf.plusDays(60), false));
        assertThat(data.versions().get(0).releaseDate()).isBefore(asOf);
        assertThat(data.team().keySet()).containsExactly("SHOP", "OPS");
    }

    @Test
    void shopHasAboutSixMonthsAnd180Issues() {
        assertThat(project("SHOP")).hasSizeBetween(160, 200);
        LocalDate first = project("SHOP").stream().map(Issue::created).min(LocalDate::compareTo).orElseThrow();
        assertThat(ChronoUnit.WEEKS.between(first, asOf)).isGreaterThanOrEqualTo(26);
    }

    @Test
    void shopThroughputIsFourToSevenPerWeekForTwelveWeeks() {
        LocalDate monday = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int w = 1; w <= 12; w++) {
            LocalDate start = monday.minusWeeks(w);
            long done = project("SHOP").stream()
                    .filter(i -> i.resolved() != null && !i.resolved().isBefore(start)
                            && i.resolved().isBefore(start.plusWeeks(1)))
                    .count();
            assertThat(done).as("week of " + start).isBetween(4L, 7L);
        }
    }

    @Test
    void shop32HasAboutTwentyRemaining() {
        assertThat(shop32().stream().filter(i -> !i.status().equals(Statuses.DONE)).count()).isBetween(18L, 22L);
    }

    @Test
    void shop32ScopeGrewEighteenPercentInTheLastTwoWeeks() {
        LocalDate cutoff = asOf.minusDays(14);
        long before = shop32().stream().filter(i -> !i.created().isAfter(cutoff)).count();
        long added = shop32().stream().filter(i -> i.created().isAfter(cutoff)).count();
        assertThat((double) added / before).isBetween(0.15, 0.21);
    }

    @Test
    void threeStoriesWaitForClientUatForSixToNineDays() {
        List<Issue> waiting = shop32().stream().filter(i -> Statuses.isClientSide(i.status())).toList();
        assertThat(waiting).hasSize(3).allMatch(i -> i.type().equals("Story"));
        assertThat(waiting).allSatisfy(i -> assertThat(daysInStatus(i)).isBetween(6L, 9L));
    }

    @Test
    void giftCardsHasFiveOrSixRemaining() {
        long open = shop32().stream()
                .filter(i -> "SHOP-E7".equals(i.epic()) && !i.status().equals(Statuses.DONE)).count();
        assertThat(open).isBetween(5L, 6L);
        assertThat(shop32()).filteredOn(i -> "SHOP-E7".equals(i.epic())).allMatch(i -> i.epicName().equals("Gift cards"));
    }

    @Test
    void arjunHoldsTheMostInProgressItems() {
        Map<String, Long> inProgress = project("SHOP").stream()
                .filter(i -> i.status().equals(Statuses.IN_PROGRESS))
                .collect(Collectors.groupingBy(Issue::assignee, Collectors.counting()));
        assertThat(inProgress.get("Arjun")).isGreaterThanOrEqualTo(5L);
        assertThat(inProgress.entrySet()).allMatch(e -> e.getKey().equals("Arjun") || e.getValue() < inProgress.get("Arjun"));
    }

    @Test
    void shopActiveItemsAreNotStale() {
        assertThat(project("SHOP")).filteredOn(i -> Statuses.isActive(i.status()))
                .allSatisfy(i -> assertThat(daysInStatus(i)).as(i.key()).isLessThan(14));
    }

    @Test
    void opsHasPoorDataQuality() {
        List<Issue> ops = project("OPS");
        assertThat(ops).hasSizeBetween(55, 65).allMatch(i -> i.fixVersion().equals("Q4"));

        List<Issue> active = ops.stream().filter(i -> Statuses.isActive(i.status())).toList();
        long stale = active.stream().filter(i -> daysInStatus(i) >= 21).count();
        assertThat((double) stale / active.size()).isBetween(0.25, 0.35);
        assertThat(active).filteredOn(i -> daysInStatus(i) < 21)
                .allSatisfy(i -> assertThat(daysInStatus(i)).isLessThan(14));

        List<Issue> stories = ops.stream().filter(i -> i.type().equals("Story")).toList();
        long missing = stories.stream().filter(i -> i.estimate() == null).count();
        assertThat((double) missing / stories.size()).isBetween(0.4, 0.6);
    }

    @Test
    void opsHasOneBulkCloseOfTwelve() {
        Map<Instant, Long> byMinute = project("OPS").stream()
                .filter(i -> i.status().equals(Statuses.DONE))
                .collect(Collectors.groupingBy(i -> i.statusHistory().getLast().at().truncatedTo(ChronoUnit.MINUTES),
                        Collectors.counting()));
        assertThat(byMinute.values().stream().filter(n -> n >= 10).toList()).containsExactly(12L);
    }

    @Test
    void absencesAreHolidaysAndAFewLeaves() {
        Map<String, List<Absence>> byType = data.absences().stream().collect(Collectors.groupingBy(Absence::type));
        assertThat(byType.get("holiday")).isNotEmpty()
                .allMatch(a -> a.person() == null && List.of("IN", "KR", "VN").contains(a.country()));
        assertThat(byType.get("holiday")).extracting(Absence::country).contains("IN", "KR", "VN");
        assertThat(byType.get("leave")).hasSizeBetween(2, 3)
                .allMatch(a -> a.person() != null && !a.person().equals("Arjun") && !a.end().isBefore(a.start()));
        assertThat(data.absences()).extracting(Absence::id).doesNotHaveDuplicates();
        assertThat(data.absences().stream().collect(Collectors.toMap(Absence::id, Function.identity()))).hasSize(
                data.absences().size());
    }
}
