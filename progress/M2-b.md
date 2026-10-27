# M2b – Snapshots (Person B, part of `m2-adapters`)

**Built**
- `engine.Snapshots.snapshot(issues, d)`: keeps issues created on or before d, cuts each status history at the end of
  day d, rebuilds `status` (last entry left) and `resolved` (date of the last move to Done while the issue is Done at d,
  else null, so a reopened issue is unresolved). Returns new records; inputs are never mutated.
- `engine.EngineDates` (package-private): shared conventions. Instants become calendar days in UTC (the zone the
  contract instants and fixtures use); the throughput window is the 12 full ISO weeks before asOf's week.
- Test support for the whole engine track in `src/test/java/com/aida/testdata/`:
  - `TestData`: fluent issue builder (`issue("SHOP-1").created(d).moved("In Progress", d2).done(d3).fixVersion("3.2")`),
    weekly-throughput generator, open-item generator, leave/holiday helpers, SHOP and OPS teams.
  - `Fixtures`: loads the shared JSON fixtures.
  - `FakeJiraSource`, `FakeLeaveSource`: in-memory implementations of the frozen interfaces.

**Verified**
- `SnapshotsTest`: resolved-after-d is unresolved at d with its status then; created-after-d is absent; Done late on d
  is resolved on d; reopened (Done -> In Progress -> Done) at each point; inputs unchanged; fixtures at their asOf
  keep every status and resolved date.
- `mvnw.cmd -B verify` green locally.

**Known gaps / notes**
- Spec reading: "resolved = date of the last move to Done at or before d" is applied only when the issue is Done at d;
  a reopened issue has resolved = null, matching Jira clearing the resolution.
