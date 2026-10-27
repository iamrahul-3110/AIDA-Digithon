# M2 part a (A2) – Adapters (`m2-adapters` once B1 Snapshots is also merged, Person A)

Branch `a/m2-adapters`, stacked on `a/m1-mock` (PR #2).

**Built**
- `HttpJiraSource` (RestClient, `aida.jira.base-url`):
  - `issues(project, release)`: `GET /rest/api/2/search` with `jql=project=KEY [AND fixVersion="NAME"]`,
    `expand=changelog`, paged `maxResults=100` until `total`; `release` null loads the whole project.
  - Mapping: `issuetype.name` → type, `status.name` → status, `customfield_10016` → estimate (null stays null),
    `parent.key` / `parent.fields.summary` → epic / epicName, `fixVersions[0].name` → fixVersion,
    `assignee.displayName` → assignee, `created` / `resolutiondate` (Jira `yyyy-MM-dd'T'HH:mm:ss.SSSZ`) → dates in
    the timestamp's own offset. statusHistory = "To Do" at `created`, then every changelog item with
    `field == "status"` in time order.
  - `releases(project)`: `GET /rest/api/2/project/{key}/versions` → `Release(project, name, releaseDate, released)`.
  - Unknown JSON properties are ignored, so real Jira's larger payloads parse too.
- `HttpLeaveSource` (RestClient, `aida.leave.base-url`): `team(project)` → `GET /team?project=`,
  `absences(from, to)` → `GET /calendar/events?from=&to=`.
- `ProjectData.load(project, asOf)`: all of the project's issues (the engine snapshots and filters by release),
  the team, the releases, and absences from asOf − 12 weeks to asOf + 6 months, filtered to the team's own leaves and
  public holidays in the team's countries. Constants `HISTORY_WEEKS = 12`, `FORECAST_MONTHS = 6`.

**Verified**
- `mvnw.cmd -B verify` → `Tests run: 165, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- `HttpAdaptersTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, adapters calling the running mock):
  - SHOP 3.2, all of SHOP (169 issues, so paging past 100 is exercised) and all of OPS come back equal to the
    generator's `Dataset`, record for record, status history included.
  - Four issues (Done, Waiting for Client UAT, To Do, In Progress) checked field by field.
  - Releases, both teams, absences in a window, a leave added through the mock store visible through the adapter.
  - `ProjectData.load` returns the right window and filters out other teams' leaves (Meera's leave is in OPS, not SHOP).

**Known gaps**
- No retry or timeout tuning on the HTTP clients; the mock is local. Add both before pointing at real Jira.
- `m2-adapters` is tagged by whoever merges second of this PR and B1 (Snapshots).
