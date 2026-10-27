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

**Pending**
- On `DataGenerator` data: OPS below 60 and SHOP 60 or above. Runs once Person A's `m1-mock` is on main.
