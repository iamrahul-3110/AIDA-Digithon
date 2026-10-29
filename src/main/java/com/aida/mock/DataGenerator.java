package com.aida.mock;

import com.aida.contract.*;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Generates demo data in memory on startup.
 * DataGenerator is profile-independent so adapters can always hit /mock/** in MVP.
 */
@Component
public class DataGenerator {

    private static final long SEED = 7L;
    private static final LocalDate AS_OF = LocalDate.now();

    // Demo data
    private final List<Issue> shopIssues = new ArrayList<>();
    private final List<Issue> opsIssues = new ArrayList<>();
    private final List<Release> shopReleases = new ArrayList<>();
    private final List<Release> opsReleases = new ArrayList<>();

    private final List<Person> shopTeam = List.of(
        new Person("Arjun", "IN"),
        new Person("Priya", "IN"),
        new Person("Rahul", "IN"),
        new Person("Ji-woo", "KR"),
        new Person("Min-jun", "KR"),
        new Person("Linh", "VN")
    );

    private final List<Person> opsTeam = List.of(
        new Person("Meera", "IN"),
        new Person("Minh", "VN")
    );

    private final List<Absence> absences = new ArrayList<>();
    private final AtomicInteger absenceIdSeq = new AtomicInteger(1000);

    public DataGenerator() {
        generate();
    }

    public synchronized void reset() {
        shopIssues.clear();
        opsIssues.clear();
        shopReleases.clear();
        opsReleases.clear();
        absences.clear();
        absenceIdSeq.set(1000);
        generate();
    }

    private void generate() {
        SplittableRandom rng = new SplittableRandom(SEED);
        LocalDate today = AS_OF;
        LocalDate sixMonthsAgo = today.minusMonths(6);

        // ---- SHOP Releases ----
        LocalDate target32 = today.plusDays(25);
        shopReleases.add(new Release("SHOP", "3.1", today.minusWeeks(6), true));
        shopReleases.add(new Release("SHOP", "3.2", target32, false));
        shopReleases.add(new Release("SHOP", "3.3", today.plusDays(90), false));

        // ---- OPS Releases ----
        opsReleases.add(new Release("OPS", "Q4", today.plusDays(60), false));

        // ---- Generate SHOP issues (about 180) ----
        // Epics
        String e7 = "SHOP-E7"; String e7Name = "Gift cards";
        String e8 = "SHOP-E8"; String e8Name = "Checkout";

        String[] assignees = {"Arjun", "Priya", "Rahul", "Ji-woo", "Min-jun", "Linh"};
        String[] types = {"Story", "Story", "Story", "Bug", "Task"};

        // Track issue keys
        int keySeq = 100;

        // SHOP 3.1 - released (all done, ~60 issues)
        for (int i = 0; i < 60; i++) {
            String key = "SHOP-" + (keySeq++);
            String epic = rng.nextBoolean() ? e7 : e8;
            String epicName = e7.equals(epic) ? e7Name : e8Name;
            LocalDate created = sixMonthsAgo.plusDays(rng.nextInt(30));
            LocalDate resolved = today.minusWeeks(7).plusDays(rng.nextInt(14));
            String assignee = assignees[rng.nextInt(assignees.length)];
            String type = types[rng.nextInt(types.length)];
            Double estimate = "Story".equals(type) ? (double) (1 + rng.nextInt(8)) : null;

            List<StatusChange> history = buildDoneHistory(created, resolved, rng);
            shopIssues.add(new Issue(key, "SHOP", type, "SHOP 3.1 story " + i, "Done",
                history, created, resolved, estimate, epic, epicName, "3.1", assignee));
        }

        // SHOP 3.2 - in progress (~85 issues, ~35 remaining)
        int remainingCount = 35;
        int doneCount32 = 50;

        // Scope added: +18% of remaining in last 14 days (about 6 items)
        int scopeAdded = (int) Math.round(remainingCount * 0.18);

        for (int i = 0; i < doneCount32; i++) {
            String key = "SHOP-" + (keySeq++);
            String epic = rng.nextBoolean() ? e7 : e8;
            String epicName = e7.equals(epic) ? e7Name : e8Name;
            LocalDate created = sixMonthsAgo.plusDays(30 + rng.nextInt(60));
            LocalDate resolved = today.minusWeeks(2).plusDays(rng.nextInt(14));
            String assignee = assignees[rng.nextInt(assignees.length)];
            String type = types[rng.nextInt(types.length)];
            Double estimate = "Story".equals(type) ? (double) (1 + rng.nextInt(8)) : null;
            List<StatusChange> history = buildDoneHistory(created, resolved, rng);
            shopIssues.add(new Issue(key, "SHOP", type, "SHOP 3.2 done " + i, "Done",
                history, created, resolved, estimate, epic, epicName, "3.2", assignee));
        }

        // Gift cards epic - 10 remaining
        for (int i = 0; i < 10; i++) {
            String key = "SHOP-" + (keySeq++);
            LocalDate created = today.minusWeeks(4).plusDays(rng.nextInt(7));
            String status = activeStatus(rng);
            String assignee = i < 3 ? "Arjun" : assignees[rng.nextInt(assignees.length)];
            Double estimate = (double)(1 + rng.nextInt(5));
            List<StatusChange> history = buildActiveHistory(created, status, rng, today);
            shopIssues.add(new Issue(key, "SHOP", "Story", "Gift cards: feature " + i, status,
                history, created, null, estimate, e7, e7Name, "3.2", assignee));
        }

        // Arjun holding many in-progress
        for (int i = 0; i < 5; i++) {
            String key = "SHOP-" + (keySeq++);
            LocalDate created = today.minusWeeks(3).plusDays(rng.nextInt(7));
            List<StatusChange> history = buildActiveHistory(created, "In Progress", rng, today);
            shopIssues.add(new Issue(key, "SHOP", "Story", "Arjun task " + i, "In Progress",
                history, created, null, 3.0, e8, e8Name, "3.2", "Arjun"));
        }

        // 3 stories in Waiting for Client UAT for 6-9 days
        for (int i = 0; i < 3; i++) {
            String key = "SHOP-" + (keySeq++);
            LocalDate created = today.minusWeeks(5);
            int waitDays = 6 + rng.nextInt(4);
            LocalDate enteredWait = today.minusDays(waitDays);
            List<StatusChange> history = buildClientWaitHistory(created, enteredWait);
            shopIssues.add(new Issue(key, "SHOP", "Story", "Awaiting UAT " + i, "Waiting for Client UAT",
                history, created, null, 5.0, e8, e8Name, "3.2", assignees[rng.nextInt(assignees.length)]));
        }

        // Remaining standard issues
        int remaining32Left = remainingCount - 10 - 5 - 3; // already added gift cards, arjun, uAT
        for (int i = 0; i < remaining32Left; i++) {
            String key = "SHOP-" + (keySeq++);
            LocalDate created = today.minusWeeks(4).plusDays(rng.nextInt(14));
            String epic = rng.nextBoolean() ? e7 : e8;
            String epicName = e7.equals(epic) ? e7Name : e8Name;
            String status = activeStatus(rng);
            String assignee = assignees[rng.nextInt(assignees.length)];
            String type = types[rng.nextInt(types.length)];
            Double estimate = "Story".equals(type) ? (rng.nextInt(10) < 2 ? null : (double)(1+rng.nextInt(8))) : null;
            List<StatusChange> history = buildActiveHistory(created, status, rng, today);
            shopIssues.add(new Issue(key, "SHOP", type, "SHOP 3.2 remaining " + i, status,
                history, created, null, estimate, epic, epicName, "3.2", assignee));
        }

        // Scope-added items (added in last 14 days)
        for (int i = 0; i < scopeAdded; i++) {
            String key = "SHOP-" + (keySeq++);
            LocalDate created = today.minusDays(rng.nextInt(14));
            String epic = rng.nextBoolean() ? e7 : e8;
            String epicName = e7.equals(epic) ? e7Name : e8Name;
            List<StatusChange> history = buildActiveHistory(created, "To Do", rng, today);
            shopIssues.add(new Issue(key, "SHOP", "Story", "Scope added " + i, "To Do",
                history, created, null, null, epic, epicName, "3.2", assignees[rng.nextInt(assignees.length)]));
        }

        // SHOP 3.3 – planned (~30 issues, all To Do)
        for (int i = 0; i < 30; i++) {
            String key = "SHOP-" + (keySeq++);
            LocalDate created = today.minusDays(rng.nextInt(7));
            List<StatusChange> history = List.of(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)));
            shopIssues.add(new Issue(key, "SHOP", "Story", "SHOP 3.3 planned " + i, "To Do",
                history, created, null, (double)(1+rng.nextInt(8)), e8, e8Name, "3.3", assignees[rng.nextInt(assignees.length)]));
        }

        // ---- Generate OPS issues (about 60) ----
        // ~30% active unchanged 21+ days, half stories without estimates, bulk close
        SplittableRandom rng2 = new SplittableRandom(SEED + 1);
        int opsKey = 200;

        // Bulk close: 12 items resolved in the same minute (for OPS quality detection)
        Instant bulkCloseInstant = today.minusDays(10).atTime(14, 0).toInstant(ZoneOffset.UTC);
        for (int i = 0; i < 12; i++) {
            String key = "OPS-" + (opsKey++);
            LocalDate created = today.minusWeeks(8).plusDays(rng2.nextInt(7));
            LocalDate resolved = today.minusDays(10);
            List<StatusChange> history = new ArrayList<>();
            history.add(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)));
            history.add(new StatusChange("In Progress", created.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            history.add(new StatusChange("Done", bulkCloseInstant));
            opsIssues.add(new Issue(key, "OPS", "Story", "Bulk closed " + i, "Done",
                history, created, resolved, rng2.nextBoolean() ? null : 3.0, null, null, "Q4",
                rng2.nextBoolean() ? "Meera" : "Minh"));
        }

        // Stale active items (30% of active, unchanged 21+ days)
        for (int i = 0; i < 18; i++) {
            String key = "OPS-" + (opsKey++);
            LocalDate created = today.minusWeeks(8).plusDays(rng2.nextInt(7));
            // Last status change was 21+ days ago
            LocalDate lastChange = today.minusDays(21 + rng2.nextInt(14));
            List<StatusChange> history = new ArrayList<>();
            history.add(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)));
            history.add(new StatusChange("In Progress", lastChange.atStartOfDay().toInstant(ZoneOffset.UTC)));
            Double estimate = rng2.nextBoolean() ? null : (double)(1 + rng2.nextInt(5));
            opsIssues.add(new Issue(key, "OPS", "Story", "OPS stale item " + i, "In Progress",
                history, created, null, estimate, null, null, "Q4",
                rng2.nextBoolean() ? "Meera" : "Minh"));
        }

        // Remaining OPS (active + done)
        for (int i = 0; i < 30; i++) {
            String key = "OPS-" + (opsKey++);
            boolean done = rng2.nextBoolean();
            LocalDate created = today.minusWeeks(6).plusDays(rng2.nextInt(14));
            LocalDate resolved = done ? today.minusDays(rng2.nextInt(20)) : null;
            String status = done ? "Done" : activeStatus(rng2);
            List<StatusChange> history = done
                ? buildDoneHistory(created, resolved, rng2)
                : buildActiveHistory(created, status, rng2, today);
            Double estimate = "Story".equals("Story") && rng2.nextBoolean() ? null : (double)(1 + rng2.nextInt(5));
            opsIssues.add(new Issue(key, "OPS", "Story", "OPS item " + i, status,
                history, created, resolved, estimate, null, null, "Q4",
                rng2.nextBoolean() ? "Meera" : "Minh"));
        }

        // ---- Absences ----
        // Public holidays (official IN/KR/VN holidays next 3 months)
        LocalDate now = today;
        // Diwali / India
        absences.add(new Absence("hol-in-1", null, "IN", now.plusDays(findNextDate(now, 10, 20)), now.plusDays(findNextDate(now, 10, 20)), "holiday"));
        // Korean National Holiday
        absences.add(new Absence("hol-kr-1", null, "KR", now.plusDays(findNextDate(now, 10, 3)), now.plusDays(findNextDate(now, 10, 3)), "holiday"));
        // VN Liberation Day
        absences.add(new Absence("hol-vn-1", null, "VN", now.plusDays(30), now.plusDays(30), "holiday"));
        // Christmas (all countries)
        LocalDate xmas = LocalDate.of(now.getYear(), 12, 25);
        if (xmas.isBefore(now)) xmas = xmas.plusYears(1);
        absences.add(new Absence("hol-all-1", null, "IN", xmas, xmas, "holiday"));
        absences.add(new Absence("hol-all-2", null, "KR", xmas, xmas, "holiday"));
        absences.add(new Absence("hol-all-3", null, "VN", xmas, xmas, "holiday"));

        // Leave: a couple of real leaves
        absences.add(new Absence("lv-" + absenceIdSeq.getAndIncrement(), "Priya", "IN",
            now.plusDays(15), now.plusDays(17), "leave"));
        absences.add(new Absence("lv-" + absenceIdSeq.getAndIncrement(), "Ji-woo", "KR",
            now.plusDays(20), now.plusDays(22), "leave"));
    }

    private int findNextDate(LocalDate now, int month, int day) {
        LocalDate target = LocalDate.of(now.getYear(), month, day);
        if (target.isBefore(now)) target = target.plusYears(1);
        return (int) ChronoUnit.DAYS.between(now, target);
    }

    private String activeStatus(SplittableRandom rng) {
        String[] statuses = {"In Progress", "In Progress", "In Review", "QA", "Waiting for Client UAT"};
        return statuses[rng.nextInt(statuses.length)];
    }

    private List<StatusChange> buildDoneHistory(LocalDate created, LocalDate resolved, SplittableRandom rng) {
        List<StatusChange> history = new ArrayList<>();
        history.add(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("In Progress", created.plusDays(1 + rng.nextInt(3)).atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("In Review", created.plusDays(2 + rng.nextInt(5)).atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("Done", resolved != null ? resolved.atStartOfDay().toInstant(ZoneOffset.UTC) : created.plusDays(10).atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.sort(Comparator.comparing(StatusChange::at));
        return history;
    }

    private List<StatusChange> buildActiveHistory(LocalDate created, String currentStatus, SplittableRandom rng, LocalDate asOf) {
        List<StatusChange> history = new ArrayList<>();
        history.add(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)));
        if (!"To Do".equals(currentStatus)) {
            LocalDate inProgressDate = created.plusDays(1 + rng.nextInt(3));
            if (!inProgressDate.isAfter(asOf)) {
                history.add(new StatusChange("In Progress", inProgressDate.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (!"In Progress".equals(currentStatus)) {
                LocalDate nextDate = inProgressDate.plusDays(1 + rng.nextInt(5));
                if (!nextDate.isAfter(asOf)) {
                    history.add(new StatusChange(currentStatus, nextDate.atStartOfDay().toInstant(ZoneOffset.UTC)));
                }
            }
        }
        history.sort(Comparator.comparing(StatusChange::at));
        return history;
    }

    private List<StatusChange> buildClientWaitHistory(LocalDate created, LocalDate enteredWait) {
        List<StatusChange> history = new ArrayList<>();
        history.add(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("In Progress", created.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("In Review", created.plusDays(5).atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("QA", created.plusDays(10).atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.add(new StatusChange("Waiting for Client UAT", enteredWait.atStartOfDay().toInstant(ZoneOffset.UTC)));
        history.sort(Comparator.comparing(StatusChange::at));
        return history;
    }

    // ---- Accessors ----
    public List<Issue> getIssues(String project) {
        return "OPS".equals(project) ? Collections.unmodifiableList(opsIssues) : Collections.unmodifiableList(shopIssues);
    }

    public List<Release> getReleases(String project) {
        return "OPS".equals(project) ? Collections.unmodifiableList(opsReleases) : Collections.unmodifiableList(shopReleases);
    }

    public List<Person> getTeam(String project) {
        return "OPS".equals(project) ? opsTeam : shopTeam;
    }

    public synchronized List<Absence> getAbsences() {
        return Collections.unmodifiableList(absences);
    }

    public synchronized Absence addLeave(String person, String country, LocalDate start, LocalDate end) {
        String id = "lv-" + absenceIdSeq.getAndIncrement();
        Absence absence = new Absence(id, person, country, start, end, "leave");
        absences.add(absence);
        return absence;
    }

    public synchronized boolean deleteLeave(String id) {
        return absences.removeIf(a -> id.equals(a.id()));
    }
}
