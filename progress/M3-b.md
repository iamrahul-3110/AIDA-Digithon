# M3b – Capacity and forecast (Person B, `m3-forecast`)

**Built**
- `engine.Capacity.byWeek(team, absences, start, weeks)`: week i = the 7 days from `start + 7i`; value = available
  person-days / (team size x 5), weekdays only, minus each person's leave and their country's public holidays
  (a leave overlapping a holiday counts once). Empty team -> 1.0 (nothing to adjust).
- `engine.ForecastEngine.forecast(...)`: on `snapshot(issues, asOf)`; remaining = release items not Done; throughput
  samples = items resolved per ISO week over the 12 full weeks before asOf's week (`windowStart` Monday ..
  `windowEnd` Sunday); weeks that ended before the first issue was created are not history and are dropped
  (`sampleWeeks` = weeks kept). Each run walks week by week from asOf: draw one sample with
  `SplittableRandom(seed)`, multiply by that week's capacity, subtract; finish = the day in the week where remaining
  reaches 0 (linear); runs cap at 104 weeks. p50/p85/p95 are nearest-rank percentiles of finish dates; `pByTarget` =
  share of runs finished on or before the target (null without one); `capacityFactor` = mean capacity over the weeks
  up to p85 (3 decimals).
- Package-private overload with a throughput scale, for WhatIfEngine (team size changes) in M5. Public signatures
  are unchanged.

**Verified** (`CapacityTest`, `ForecastEngineTest`)
- identical input -> identical output; more leave -> later or equal p85 (strictly later with heavy leave);
  earlier target -> lower or equal pByTarget (down to 0.0); 10,000 runs under 1 second; zero remaining ->
  p50 = p85 = p95 = asOf and pByTarget 1.0; constant 5/week with 20 remaining -> asOf + 27 exactly;
  no throughput -> capped at 104 weeks; short history samples only weeks since the first issue.
- `mvnw.cmd -B verify` green locally.

- Each run draws from its own `SplittableRandom.split()` stream, so run r sees the same draws whatever the other
  runs consume: less scope or more capacity can never make a single run finish later (the what-if and leave
  monotonicity checks hold exactly, not just on average).

**Calibration** (2026-10-27, against `origin/a/m1-mock` merged into a scratch worktree, not yet on main)
- `DataGenerator.generate(2026-10-27, 7)`, SHOP 3.2, target 2026-11-21 (asOf + 25):
  remaining 18; throughput samples `[4, 7, 7, 5, 4, 4, 6, 5, 5, 4, 6, 5]` (mean 5.2); capacity 0.953;
  **p50 2026-11-21, p85 2026-11-24, p95 2026-11-25, pByTarget 0.63** -> p85 is target + 3 days.
- Scenario 1 wants pByTarget 0.35-0.55 and p85 target + 7-14. Both miss on the optimistic side.
  Scenario 3 (drop SHOP-E7, 13 remaining) gives pByTarget 1.0 against 0.70-0.90.
- **Which side:** data. The engine is following the spec: ~18 items at ~5.2/week x 0.95 capacity is ~25 days,
  so p50 lands on the target. The problem is the spread. Weekly throughput of 4-7 averaged over ~4 drawn weeks
  puts only 4 days between p50 and p95. To get p85 7-14 days past target with p50 near target, the throughput
  history needs much more week-to-week variance (some 0-2 weeks, some 8+), or more remaining items or less
  capacity in the window. With 4-7 per week, scenario 1's ranges may not be reachable at all; settle this at S4.
  Engine rules unchanged.
