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

**Calibration**
- Pending: Person A's `m1-mock` is not on main yet. Once it is, run ForecastEngine on
  `DataGenerator.generate(today, 7)` for SHOP 3.2 and record the numbers here against scenario 1
  (pByTarget 0.35-0.55, p85 7-14 days after target).
