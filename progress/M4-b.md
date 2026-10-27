# M4b – QualityEngine (Person B, needed for `m4-e2e`)

**Built**
- `engine.QualityEngine.assess(issues, asOf)` on `snapshot(issues, asOf)`:
  - `stale_status`: active items (In Progress, In Review, QA, Waiting for Client UAT) whose last status change is
    14+ days before asOf.
  - `missing_estimate`: Stories with a null estimate (Bugs and Tasks are not counted).
  - `bulk_close`: Done issues whose last move to Done falls in the same UTC minute, in groups of 10+.
  - score = 100 - 50 x stale share of active items - 30 x missing share of stories - 15 if any bulk close,
    clamped to 0..100, rounded half up.
  - `forecastAllowed` = score >= 60 and 6+ weeks between the first issue's creation and asOf.
  - Only rules that found something are listed; each has one plain sentence with numbers, e.g.
    "3 of 10 active items (30%) have not changed status in 14+ days."

**Verified** (`QualityEngineTest`)
- One test per rule (boundaries: exactly 14 days is stale, 9 in one minute is not a bulk close, Bugs without
  estimates do not count), the full formula with clamping, half-up rounding (93.75 -> 94), a score below 60 and a
  short history each blocking the forecast, and the fixtures' one missing estimate.
- `mvnw.cmd -B verify` green locally.

**On DataGenerator data** (2026-10-27, `origin/a/m1-mock` in a scratch worktree, seed 7)
- SHOP: score **99**, forecastAllowed true (2 of 99 stories without estimate).
- OPS: score **54**, forecastAllowed false: 6 of 20 active items (30%) stale; 13 of 25 stories (52%) without
  estimate; 12 items bulk-closed in the same minute. Both meet the targets.
- After `m1-mock` merges, add this as a regular test (`DataGenerator.generate(asOf, 7)`: OPS < 60, SHOP >= 60).

**Notes**
- Zone: the generator turns instants into dates in Asia/Kolkata, the engine in UTC (the contract and fixtures
  use UTC instants). They only disagree for status changes between 18:30 and 24:00 UTC; worth aligning at S4.
