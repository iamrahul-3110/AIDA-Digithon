package com.aida.mock;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Release;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;

/**
 * Builds the seeded demo dataset. Pure: the same asOf and seed always give the same data. Signature FROZEN.
 *
 * <p>Planted for the demo (see "Demo data targets" in the spec):
 * <ul>
 * <li>SHOP: about 180 issues over 6 months, 4-7 items Done per ISO week; 3.1 released, 3.2 due asOf + 25 days with
 * about 20 remaining, 3.3 planned.</li>
 * <li>SHOP 3.2: scope +18% in the last 14 days; 3 stories in Waiting for Client UAT for 6-9 days; epic SHOP-E7
 * "Gift cards" with 5-6 remaining items; Arjun holding 5 in-progress items.</li>
 * <li>OPS (release Q4): about 60 issues; 30% of active items unchanged for 21+ days; half the stories without
 * estimates; 12 items bulk-closed in the same minute.</li>
 * <li>Public holidays for IN, KR and VN around asOf, plus 3 leaves (none for Arjun: demo scenario 2 adds his).</li>
 * </ul>
 *
 * <p>All timestamps fall between 09:00 and 18:00 IST, so an instant's date is the same in IST and UTC.
 */
public final class DataGenerator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    public static final List<Person> SHOP_TEAM = List.of(
            new Person("Arjun", "IN"), new Person("Priya", "IN"), new Person("Rahul", "IN"),
            new Person("Ji-woo", "KR"), new Person("Min-jun", "KR"), new Person("Linh", "VN"));

    public static final List<Person> OPS_TEAM = List.of(new Person("Meera", "IN"), new Person("Minh", "VN"));

    record Holiday(String country, LocalDate date, String name) {
    }

    /**
     * Public holidays for IN, KR and VN, hardcoded for the demo. VERIFY every date against the official government
     * calendars before relying on it (several follow the lunar calendar and move every year), and extend the list
     * when the demo date moves past it. Only holidays from 12 weeks before asOf to 3 months after it are used.
     */
    static final List<Holiday> PUBLIC_HOLIDAYS = List.of(
            new Holiday("VN", LocalDate.of(2026, 9, 2), "National Day"),
            new Holiday("IN", LocalDate.of(2026, 10, 2), "Gandhi Jayanti"),
            new Holiday("KR", LocalDate.of(2026, 10, 3), "National Foundation Day"),
            new Holiday("KR", LocalDate.of(2026, 10, 9), "Hangul Day"),
            new Holiday("IN", LocalDate.of(2026, 11, 8), "Diwali"),
            new Holiday("IN", LocalDate.of(2026, 11, 24), "Guru Nanak Jayanti"),
            new Holiday("IN", LocalDate.of(2026, 12, 25), "Christmas Day"),
            new Holiday("KR", LocalDate.of(2026, 12, 25), "Christmas Day"),
            new Holiday("KR", LocalDate.of(2027, 1, 1), "New Year's Day"),
            new Holiday("VN", LocalDate.of(2027, 1, 1), "New Year's Day"),
            new Holiday("IN", LocalDate.of(2027, 1, 26), "Republic Day"));

    static final int HISTORY_WEEKS = 26;
    static final int SHOP_31_WEEKS_AGO = 8;
    static final int TARGET_DAYS = 25;
    static final double SCOPE_GROWTH = 0.18;

    private DataGenerator() {
    }

    public static Dataset generate(LocalDate asOf, long seed) {
        return new Builder(asOf, new SplittableRandom(seed)).build();
    }

    /** Mutable issue under construction; keys are assigned at the end in creation order, like Jira. */
    private static final class Draft {
        final String project;
        final String type;
        final List<StatusChange> history = new ArrayList<>();
        String summary;
        Double estimate;
        String epic;
        String epicName;
        String fixVersion;
        String assignee;

        Draft(String project, String type) {
            this.project = project;
            this.type = type;
        }

        Instant createdAt() {
            return history.getFirst().at();
        }

        String status() {
            return history.getLast().status();
        }
    }

    private static final class Builder {
        private static final Map<String, String> EPICS = Map.of(
                "SHOP-E4", "Mobile performance", "SHOP-E5", "Checkout revamp", "SHOP-E6", "Search improvements",
                "SHOP-E7", "Gift cards", "SHOP-E8", "Loyalty points",
                "OPS-E1", "Monitoring", "OPS-E2", "Cost reduction");
        private static final Map<String, String[]> TOPICS = Map.of(
                "SHOP-E4", new String[] {"Lazy-load product images", "Cache category pages", "Shrink app bundle",
                        "Speed up cart rendering", "Prefetch checkout assets", "Reduce API round trips"},
                "SHOP-E5", new String[] {"One-page checkout", "Saved addresses", "Apple Pay support",
                        "Address autocomplete", "Coupon field on mobile", "Order summary redesign", "Tax display"},
                "SHOP-E6", new String[] {"Search typo tolerance", "Search filters", "Search result ranking",
                        "Recent searches", "Search suggestions", "Zero-result page"},
                "SHOP-E7", new String[] {"Gift card balance API", "Redeem gift card at checkout",
                        "Gift card purchase page", "Gift card email delivery", "Gift card admin report",
                        "Gift card fraud rules", "Gift card refunds", "Partial gift card payment"},
                "SHOP-E8", new String[] {"Loyalty points ledger", "Points on order", "Points redemption",
                        "Loyalty tier badges", "Points expiry job"},
                "OPS-E1", new String[] {"Disk usage alert", "Service health dashboard", "On-call runbook",
                        "Log retention policy", "Latency SLO alert", "Synthetic checks"},
                "OPS-E2", new String[] {"Right-size staging nodes", "Spot instances for CI", "Unused volume cleanup",
                        "Reserved instance review", "CDN cost report"});

        private final LocalDate asOf;
        private final SplittableRandom rnd;
        private final LocalDate currentMonday;
        private final List<Draft> shop = new ArrayList<>();
        private final List<Draft> ops = new ArrayList<>();
        private final List<Release> releases = new ArrayList<>();

        Builder(LocalDate asOf, SplittableRandom rnd) {
            this.asOf = asOf;
            this.rnd = rnd;
            this.currentMonday = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        Dataset build() {
            buildShop();
            buildOps();
            List<Issue> issues = new ArrayList<>(toIssues(shop));
            issues.addAll(toIssues(ops));
            Map<String, List<Person>> team = new LinkedHashMap<>();
            team.put("SHOP", SHOP_TEAM);
            team.put("OPS", OPS_TEAM);
            return new Dataset(List.copyOf(issues), List.copyOf(releases), team, absences());
        }

        // ---------------------------------------------------------------- SHOP

        private void buildShop() {
            LocalDate release31 = currentMonday.minusWeeks(SHOP_31_WEEKS_AGO).plusDays(4);
            LocalDate target32 = asOf.plusDays(TARGET_DAYS);
            releases.add(new Release("SHOP", "3.1", release31, true));
            releases.add(new Release("SHOP", "3.2", target32, false));
            releases.add(new Release("SHOP", "3.3", target32.plusWeeks(6), false));

            // Throughput history: 4-7 items Done in every full ISO week before asOf's week.
            List<Draft> done32 = new ArrayList<>();
            int missingEstimates = 0;
            for (int w = HISTORY_WEEKS; w >= 1; w--) {
                LocalDate monday = currentMonday.minusWeeks(w);
                int count = 4 + rnd.nextInt(4);
                for (int i = 0; i < count; i++) {
                    LocalDate doneDay = monday.plusDays(rnd.nextInt(5));
                    boolean in31 = !doneDay.isAfter(release31);
                    Draft d = new Draft("SHOP", randomType());
                    doneHistory(d, doneDay);
                    if (in31) {
                        d.fixVersion = "3.1";
                        epic(d, rnd.nextBoolean() ? "SHOP-E4" : "SHOP-E5");
                        if (d.type.equals("Story") && missingEstimates < 2 && rnd.nextInt(10) == 0) {
                            d.estimate = null;
                            missingEstimates++;
                        }
                    } else {
                        d.fixVersion = "3.2";
                        clampCreated(d, asOf.minusDays(15));
                        done32.add(d);
                    }
                    d.assignee = randomShopPerson();
                    shop.add(d);
                }
            }
            // About 6 of the Done 3.2 items belong to Gift cards; the rest to checkout and search.
            for (int i = 0; i < done32.size(); i++) {
                epic(done32.get(i), i % 7 == 3 ? "SHOP-E7" : (rnd.nextBoolean() ? "SHOP-E5" : "SHOP-E6"));
            }

            // 3.2 work still open that existed 14+ days ago: 3 UAT stories, 2 QA, 2 In Review, 3 In Progress.
            List<Draft> old = new ArrayList<>();
            // Entered UAT 6-9 days ago, on weekdays (any 4 consecutive days hold at least 2).
            List<LocalDate> uatDays = new ArrayList<>();
            for (int days : new int[] {6, 9, 7, 8}) {
                LocalDate day = asOf.minusDays(days);
                if (prevWeekday(day).equals(day)) {
                    uatDays.add(day);
                }
            }
            for (int i = 0; i < 3; i++) {
                old.add(active("SHOP", "Story", Statuses.WAITING_FOR_CLIENT_UAT, uatDays.get(i % uatDays.size()), 15));
            }
            for (int i = 0; i < 2; i++) {
                old.add(active("SHOP", randomType(), Statuses.QA, asOf.minusDays(2 + rnd.nextInt(6)), 15));
            }
            for (int i = 0; i < 2; i++) {
                old.add(active("SHOP", randomType(), Statuses.IN_REVIEW, asOf.minusDays(1 + rnd.nextInt(6)), 15));
            }
            for (int i = 0; i < 3; i++) {
                old.add(active("SHOP", randomType(), Statuses.IN_PROGRESS, asOf.minusDays(1 + rnd.nextInt(10)), 15));
            }
            old.get(7).assignee = "Arjun";
            old.get(8).assignee = "Arjun";

            // Scope growth: items created in the last 14 days = 18% of the scope 14 days ago.
            int scopeBefore = done32.size() + old.size();
            int added = (int) Math.round(SCOPE_GROWTH * scopeBefore);
            List<Draft> fresh = new ArrayList<>();
            for (int i = 0; i < added; i++) {
                LocalDate created = asOf.minusDays(2 + rnd.nextInt(12));
                Draft d = new Draft("SHOP", randomType());
                d.history.add(new StatusChange(Statuses.TO_DO, at(created)));
                if (i < 3) {
                    // Arjun picks up new work while holding older items: 5 in progress in total.
                    d.history.add(new StatusChange(Statuses.IN_PROGRESS, at(created.plusDays(1))));
                    d.assignee = "Arjun";
                }
                fresh.add(d);
            }

            // Gift cards keeps 5-6 open items: one per active stage, the rest fresh To Do items.
            int giftCardsOpen = 5 + rnd.nextInt(2);
            List<Draft> giftCards = new ArrayList<>(List.of(old.get(0), old.get(3), old.get(5), old.get(7)));
            for (Draft d : fresh) {
                if (giftCards.size() < giftCardsOpen && d.status().equals(Statuses.TO_DO)) {
                    giftCards.add(d);
                }
            }
            List<Draft> open = new ArrayList<>(old);
            open.addAll(fresh);
            for (Draft d : open) {
                d.fixVersion = "3.2";
                epic(d, giftCards.contains(d) ? "SHOP-E7" : (rnd.nextBoolean() ? "SHOP-E5" : "SHOP-E6"));
                if (d.assignee == null && !d.status().equals(Statuses.TO_DO)) {
                    d.assignee = randomShopPerson("Arjun");
                } else if (d.assignee == null && rnd.nextBoolean()) {
                    d.assignee = randomShopPerson("Arjun");
                }
            }
            shop.addAll(open);

            // 3.3 planned: To Do items created over the last 6 weeks.
            for (int i = 0; i < 15; i++) {
                Draft d = new Draft("SHOP", randomType());
                d.history.add(new StatusChange(Statuses.TO_DO, at(prevWeekday(asOf.minusDays(1 + rnd.nextInt(42))))));
                d.fixVersion = "3.3";
                epic(d, rnd.nextInt(3) == 0 ? "SHOP-E6" : "SHOP-E8");
                shop.add(d);
            }
        }

        // ---------------------------------------------------------------- OPS

        private void buildOps() {
            releases.add(new Release("OPS", "Q4", asOf.plusDays(60), false));
            List<Draft> drafts = new ArrayList<>();

            // 18 items closed normally over the last 16 weeks.
            for (int i = 0; i < 18; i++) {
                LocalDate doneDay = currentMonday.minusWeeks(1 + rnd.nextInt(16)).plusDays(rnd.nextInt(5));
                Draft d = new Draft("OPS", randomOpsType());
                doneHistory(d, doneDay);
                drafts.add(d);
            }

            // One clean-up: 12 items closed in the same minute.
            LocalDate bulkDay = prevWeekday(asOf.minusDays(20));
            Instant bulkAt = bulkDay.atTime(17, 42).atZone(ZONE).toInstant();
            for (int i = 0; i < 12; i++) {
                Draft d = new Draft("OPS", randomOpsType());
                LocalDate created = prevWeekday(bulkDay.minusDays(10 + rnd.nextInt(60)));
                d.history.add(new StatusChange(Statuses.TO_DO, at(created)));
                if (rnd.nextBoolean()) {
                    d.history.add(new StatusChange(Statuses.IN_PROGRESS,
                            at(prevWeekday(bulkDay.minusDays(1 + rnd.nextInt(9))))));
                }
                d.history.add(new StatusChange(Statuses.DONE, bulkAt));
                drafts.add(d);
            }

            // 20 active items: 6 (30%) untouched for 21+ days, 14 moved in the last 10 days.
            String[] stages = {Statuses.IN_PROGRESS, Statuses.IN_REVIEW, Statuses.QA};
            for (int i = 0; i < 20; i++) {
                boolean stale = i < 6;
                LocalDate entered = stale ? asOf.minusDays(21 + rnd.nextInt(25)) : asOf.minusDays(1 + rnd.nextInt(10));
                drafts.add(active("OPS", randomOpsType(), stages[rnd.nextInt(stages.length)], entered, 1));
            }

            // 10 items waiting in To Do.
            for (int i = 0; i < 10; i++) {
                Draft d = new Draft("OPS", randomOpsType());
                d.history.add(new StatusChange(Statuses.TO_DO, at(prevWeekday(asOf.minusDays(1 + rnd.nextInt(60))))));
                drafts.add(d);
            }

            int stories = 0;
            for (Draft d : drafts) {
                d.fixVersion = "Q4";
                epic(d, rnd.nextBoolean() ? "OPS-E1" : "OPS-E2");
                d.assignee = OPS_TEAM.get(rnd.nextInt(OPS_TEAM.size())).name();
                if (d.type.equals("Story") && stories++ % 2 == 0) {
                    d.estimate = null;
                }
            }
            ops.addAll(drafts);
        }

        // ---------------------------------------------------------------- absences

        private List<Absence> absences() {
            List<Absence> result = new ArrayList<>();
            LocalDate from = asOf.minusWeeks(12);
            LocalDate to = asOf.plusMonths(3);
            for (Holiday h : PUBLIC_HOLIDAYS) {
                if (!h.date().isBefore(from) && !h.date().isAfter(to)) {
                    result.add(new Absence("hol-" + h.country().toLowerCase() + "-" + h.date(), null, h.country(),
                            h.date(), h.date(), "holiday"));
                }
            }
            result.add(leave(1, "Priya", "IN", nextWeekday(asOf.plusDays(8)), 3));
            result.add(leave(2, "Ji-woo", "KR", nextWeekday(asOf.plusDays(33)), 2));
            result.add(leave(3, "Meera", "IN", nextWeekday(asOf.plusDays(15)), 5));
            return List.copyOf(result);
        }

        private static Absence leave(int n, String person, String country, LocalDate start, int weekdays) {
            LocalDate end = start;
            for (int i = 1; i < weekdays; i++) {
                end = nextWeekday(end.plusDays(1));
            }
            return new Absence("lv-" + n, person, country, start, end, "leave");
        }

        // ---------------------------------------------------------------- helpers

        /** Full workflow ending Done on {@code doneDay}; earlier stages on distinct earlier weekdays. */
        private void doneHistory(Draft d, LocalDate doneDay) {
            List<String> path = new ArrayList<>(List.of(Statuses.IN_PROGRESS, Statuses.IN_REVIEW, Statuses.QA));
            if (d.type.equals("Story") && rnd.nextInt(7) == 0) {
                path.add(Statuses.WAITING_FOR_CLIENT_UAT);
            }
            path.add(Statuses.DONE);
            List<LocalDate> days = backwards(doneDay, path.size(), 1, 3);
            LocalDate created = prevWeekday(days.getFirst().minusDays(1 + rnd.nextInt(21)));
            d.history.add(new StatusChange(Statuses.TO_DO, at(created)));
            for (int i = 0; i < path.size(); i++) {
                d.history.add(new StatusChange(path.get(i), at(days.get(i))));
            }
        }

        /** An open item that entered {@code status} on {@code entered}, having passed every earlier active stage. */
        private Draft active(String project, String type, String status, LocalDate entered, int minAgeDays) {
            Draft d = new Draft(project, type);
            int stage = Statuses.WORKFLOW.indexOf(status);
            List<String> path = Statuses.WORKFLOW.subList(1, stage + 1);
            List<LocalDate> days = backwards(entered, path.size(), 1, 4);
            LocalDate created = prevWeekday(days.getFirst().minusDays(minAgeDays + rnd.nextInt(20)));
            d.history.add(new StatusChange(Statuses.TO_DO, at(created)));
            for (int i = 0; i < path.size(); i++) {
                d.history.add(new StatusChange(path.get(i), at(days.get(i))));
            }
            return d;
        }

        /** {@code n} strictly increasing weekdays ending on {@code last} (moved back to a weekday if needed). */
        private List<LocalDate> backwards(LocalDate last, int n, int minGap, int maxGap) {
            List<LocalDate> days = new ArrayList<>();
            LocalDate d = prevWeekday(last);
            days.add(d);
            for (int i = 1; i < n; i++) {
                d = prevWeekday(d.minusDays(minGap + rnd.nextInt(maxGap - minGap + 1)));
                days.addFirst(d);
            }
            return days;
        }

        private static void clampCreated(Draft d, LocalDate latest) {
            StatusChange first = d.history.getFirst();
            if (LocalDate.ofInstant(first.at(), ZONE).isAfter(latest)) {
                d.history.set(0, new StatusChange(first.status(), latest.atTime(9, 30).atZone(ZONE).toInstant()));
            }
        }

        private void epic(Draft d, String epic) {
            d.epic = epic;
            d.epicName = EPICS.get(epic);
            String[] topics = TOPICS.get(epic);
            d.summary = topics[rnd.nextInt(topics.length)];
            d.estimate = estimate(d.type);
        }

        private Double estimate(String type) {
            return switch (type) {
                case "Story" -> new double[] {1, 2, 3, 5, 8}[rnd.nextInt(5)];
                case "Bug" -> (double) (1 + rnd.nextInt(2));
                default -> (double) (1 + rnd.nextInt(3));
            };
        }

        private String randomType() {
            int r = rnd.nextInt(20);
            return r < 12 ? "Story" : r < 17 ? "Bug" : "Task";
        }

        private String randomOpsType() {
            int r = rnd.nextInt(10);
            return r < 5 ? "Story" : r < 8 ? "Task" : "Bug";
        }

        private String randomShopPerson(String... exclude) {
            List<Person> pool = SHOP_TEAM.stream().filter(p -> !List.of(exclude).contains(p.name())).toList();
            return pool.get(rnd.nextInt(pool.size())).name();
        }

        /** A random time between 09:00 and 18:00 IST on {@code day}. */
        private Instant at(LocalDate day) {
            return day.atTime(9, 0).plusMinutes(rnd.nextInt(540)).atZone(ZONE).toInstant();
        }

        private static List<Issue> toIssues(List<Draft> drafts) {
            List<Draft> sorted = new ArrayList<>(drafts);
            sorted.sort(Comparator.comparing(Draft::createdAt));
            List<Issue> issues = new ArrayList<>();
            int n = 1;
            for (Draft d : sorted) {
                StatusChange last = d.history.getLast();
                LocalDate resolved = last.status().equals(Statuses.DONE) ? LocalDate.ofInstant(last.at(), ZONE) : null;
                issues.add(new Issue(d.project + "-" + n++, d.project, d.type, d.summary, d.status(),
                        List.copyOf(d.history), LocalDate.ofInstant(d.createdAt(), ZONE), resolved, d.estimate,
                        d.epic, d.epicName, d.fixVersion, d.assignee));
            }
            return issues;
        }
    }

    static LocalDate prevWeekday(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case SATURDAY -> d.minusDays(1);
            case SUNDAY -> d.minusDays(2);
            default -> d;
        };
    }

    static LocalDate nextWeekday(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case SATURDAY -> d.plusDays(2);
            case SUNDAY -> d.plusDays(1);
            default -> d;
        };
    }
}
