package com.aida.testdata;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.StatusChange;
import com.aida.contract.Statuses;

/**
 * Fluent builders for engine tests, so any rule can be tested without the mock:
 * {@code issue("SHOP-1").created(d).moved("In Progress", d2).done(d3).fixVersion("3.2").build()}.
 */
public final class TestData {

    /** The SHOP team from the shared spec. */
    public static final List<Person> SHOP_TEAM = List.of(
            new Person("Arjun", "IN"), new Person("Priya", "IN"), new Person("Rahul", "IN"),
            new Person("Ji-woo", "KR"), new Person("Min-jun", "KR"), new Person("Linh", "VN"));

    /** The OPS team from the shared spec. */
    public static final List<Person> OPS_TEAM = List.of(new Person("Meera", "IN"), new Person("Minh", "VN"));

    private TestData() {
    }

    public static IssueBuilder issue(String key) {
        return new IssueBuilder(key);
    }

    /** 10:00 UTC on {@code d}; the time every builder move uses unless an Instant is given. */
    public static Instant at(LocalDate d) {
        return d.atTime(10, 0).toInstant(ZoneOffset.UTC);
    }

    /** Monday of the ISO week containing {@code d}. */
    public static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Done issues giving the requested throughput: {@code perWeek[i]} items resolved on the Wednesday of
     * the i-th of the {@code perWeek.length} ISO weeks before asOf's week (oldest first). Keys are
     * {@code <project>-T<n>}; each issue is created 14 days before it is done.
     */
    public static List<Issue> throughput(String project, String release, LocalDate asOf, int... perWeek) {
        List<Issue> out = new ArrayList<>();
        LocalDate firstMonday = monday(asOf).minusWeeks(perWeek.length);
        int n = 0;
        for (int w = 0; w < perWeek.length; w++) {
            LocalDate doneOn = firstMonday.plusWeeks(w).plusDays(2);
            for (int i = 0; i < perWeek[w]; i++) {
                n++;
                out.add(issue(project + "-T" + n).created(doneOn.minusDays(14))
                        .moved(Statuses.IN_PROGRESS, doneOn.minusDays(7)).done(doneOn).fixVersion(release).build());
            }
        }
        return out;
    }

    /** {@code count} issues still in {@code status} on asOf, keys {@code <project>-R<n>}, all moved there on {@code since}. */
    public static List<Issue> open(String project, String release, String status, LocalDate since, int count) {
        List<Issue> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            IssueBuilder b = issue(project + "-R" + status.replace(' ', '_') + i).created(since.minusDays(1))
                    .fixVersion(release);
            if (!Statuses.TO_DO.equals(status)) {
                b.moved(status, since);
            }
            out.add(b.build());
        }
        return out;
    }

    public static Absence leave(String id, String person, String country, LocalDate start, LocalDate end) {
        return new Absence(id, person, country, start, end, "leave");
    }

    public static Absence holiday(String country, LocalDate day) {
        return new Absence("hol-" + country.toLowerCase() + "-" + day, null, country, day, day, "holiday");
    }

    public static final class IssueBuilder {

        private final String key;
        private String project;
        private String type = "Story";
        private String summary;
        private LocalDate created;
        private final List<StatusChange> history = new ArrayList<>();
        private Double estimate = 3.0;
        private String epic;
        private String epicName;
        private String fixVersion;
        private String assignee;

        private IssueBuilder(String key) {
            this.key = key;
            this.project = key.contains("-") ? key.substring(0, key.indexOf('-')) : key;
            this.summary = "Summary of " + key;
        }

        /** Creation date; also the first history entry, "To Do". Call first. */
        public IssueBuilder created(LocalDate d) {
            this.created = d;
            history.add(0, new StatusChange(Statuses.TO_DO, at(d)));
            return this;
        }

        public IssueBuilder moved(String status, LocalDate d) {
            return moved(status, at(d));
        }

        public IssueBuilder moved(String status, Instant when) {
            history.add(new StatusChange(status, when));
            return this;
        }

        public IssueBuilder done(LocalDate d) {
            return moved(Statuses.DONE, d);
        }

        public IssueBuilder done(Instant when) {
            return moved(Statuses.DONE, when);
        }

        public IssueBuilder project(String project) {
            this.project = project;
            return this;
        }

        public IssueBuilder type(String type) {
            this.type = type;
            return this;
        }

        public IssueBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        /** null = missing estimate. */
        public IssueBuilder estimate(Double estimate) {
            this.estimate = estimate;
            return this;
        }

        public IssueBuilder epic(String epic, String epicName) {
            this.epic = epic;
            this.epicName = epicName;
            return this;
        }

        public IssueBuilder fixVersion(String fixVersion) {
            this.fixVersion = fixVersion;
            return this;
        }

        public IssueBuilder assignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        /** Status is the last history entry; resolved is the date of the last move to Done when the issue ends Done. */
        public Issue build() {
            if (created == null) {
                throw new IllegalStateException(key + ": call created(..) first");
            }
            StatusChange last = history.get(history.size() - 1);
            LocalDate resolved = Statuses.DONE.equals(last.status())
                    ? LocalDate.ofInstant(last.at(), ZoneOffset.UTC)
                    : null;
            return new Issue(key, project, type, summary, last.status(), List.copyOf(history), created, resolved,
                    estimate, epic, epicName, fixVersion, assignee);
        }
    }
}
