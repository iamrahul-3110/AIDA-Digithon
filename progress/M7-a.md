# M7 (A5) – Alerts and demo scenarios (`m7-demo` after SYNC S4, Person A)

Branch `a/m7-alerts`, rebased onto main at `f729eeb` (after #13, Person B's reports and skill).

**Built**
- `alerts.AlertService`: for every project in `aida.alerts.projects` (default SHOP, OPS) whose data quality allows a
  forecast, forecasts every unreleased release (target = its release date) and compares with the saved forecast in
  `data/alert-state.json`; a release with no saved state is compared with its forecast as of 7 days earlier, so the
  first run already reports last week's change. Alerts when pByTarget drops 15+ points (`confidence_drop`) or p85
  moves 5+ days (`p85_later` / `p85_earlier`), with one plain sentence. Every alert is logged (WARN) and appended to
  `data/alerts.log`; when `aida.alerts.webhook` is set the run POSTs `{"text", "alerts"}` (a failing webhook is
  logged, never fails the run). State is saved after every run. `@Scheduled` on `aida.alerts.cron`.
- `alerts.AlertController`: `POST /admin/alerts/run` (scope admin) returns `{ranAt, asOf, checked, skipped, alerts}`;
  also enables scheduling.
- `POST /mock/admin/reset` now also clears the alert state, so a demo run after a reset is repeatable.
- `demo.DemoScenariosTest`: scenarios 1-6 end to end through the MCP SDK client with a bearer token, after a reset,
  on the demo date, real engines. Tagged `demo`; excluded from the default build by the `aida.excluded-test-groups`
  property and run with `./mvnw verify -Pdemo`. Scenario 5 skips itself (assumption) if `build_report` is missing.
- Test support: `alerts.DemoClock` pins "today" to the mock's demo date (2026-10-27) for tests that compute dates.

**Verified**
- `mvnw.cmd -B verify` → `Tests run: 256, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (demo excluded).
- `AlertServiceTest` (real engines on the mock over HTTP, local webhook receiver): the first run after a reset
  checks SHOP/3.2 and SHOP/3.3, skips OPS for data quality, and alerts
  "SHOP 3.2: chance of making the target fell from 100% to 63%; p85 moved 9 days later, from 2026-11-15 to
  2026-11-24 (compared with 2026-10-20)."; alerts.log, alert-state.json and the webhook body are written; a second
  run is quiet; after adding 5 days of leave for Arjun the next run compares with the previous run
  ("fell from 63% to 45%"); `/admin/alerts/run` → 401 without a token, 403 with an mcp-only token, 200 with admin;
  threshold rules (exactly 15 points alerts, 14 does not; ±5 days; missing pByTarget ignored; message text).
- `mvnw.cmd -B verify -Pdemo` on main with B5 merged (DemoScenariosTest: 6 run, 2 failures, 0 skipped):

| Scenario | Result | Value |
| --- | --- | --- |
| 1. SHOP 3.2 by target | **fails** | pByTarget 0.6282 (want 0.35-0.55); p85 3 days after target (want 7-14) |
| 2. Arjun leave | passes | p85 later or equal; explain_change lists a leave driver |
| 3. Drop Gift cards | **fails** | pByTarget 1.0 (want 0.70-0.90) |
| 4. OPS quality | passes | score < 60, forecastAllowed false, stale items listed |
| 5. Reports | passes | team, management, client, hq; the client report names no SHOP or OPS team member |
| 6. Alert run | passes | SHOP 3.2 `confidence_drop` |

**Known gaps – spec conflict for SYNC S4 (STOP rule: needs a decision, not tuning)**
- Scenarios 1 and 3 cannot both be met with "12-week throughput 4-7 items per week" under the engine rule "each
  run draws one historical week per future week". Scenario 3 drops 5-6 items (about one week of work) and must move
  pByTarget from ~0.45 to 0.70-0.90; that needs the finish dates to spread with a standard deviation of about 7 days,
  which scenario 1's "p85 7-14 days after target" also implies. Weekly draws of 4-7 (sd about 1.1 items) over the
  ~4 remaining weeks spread the finish by about 3 days (sd about 1.1 x √4 / 5.5 weeks). Today: p85 - p50 = 3 days,
  and dropping Gift cards gives 1.0.
- Ways out, each needing agreement: (a) widen the generated weekly throughput (roughly 1-10 per week) — changes a
  data target; (b) an engine rule with more variance (Person B) — changes an engine rule; (c) relax the scenario
  1 and 3 ranges to what 4-7 per week produces — changes the demo targets.
- `m7-demo` is tagged when all six pass in CI and the demo tag is no longer excluded.
