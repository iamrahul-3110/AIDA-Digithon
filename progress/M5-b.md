# M5b – Explanations, what-ifs, risks (Person B, `m5-analysis`)

**Built**
- `engine.RiskEngine.assess`: active items with days in their current status; one reason each, checked in order
  client_wait (Waiting for Client UAT 5+ days, side client), stale (14+ days, side team), aging (more than twice the
  median days of active items in the same status); side is client for client-side statuses; bottleneckStage =
  active status with the most item-days; sorted by days desc, then key.
- `engine.WhatIfEngine.run`: drops the not-Done release items of each epic in `dropEpics` (by epic key or epic name,
  case-insensitive), removes and adds people, scales every throughput sample by new / old team size and recomputes
  capacity with the new team; 10,000 runs, seed 42; never mutates inputs.
- `engine.ExplainEngine.explain`: forecasts at fromDate and toDate (own snapshot each, same seed); shiftDays =
  p85To - p85From. Candidate drivers with signed raw weights in days:
  scope_added (release items created after fromDate, one item of throughput each), client_wait (UAT 5+ days at
  toDate, one item each), leave (leave + holiday person-days in the toDate run's window minus the fromDate run's,
  in team-days), aging (half an item each), throughput_drop (only when the last 4 weeks are below the 12-week
  average; the change in days the remaining items need at the two runs' average throughput). Weights are scaled
  to tenths of a day so impactDays sum exactly to shiftDays (remainder on the largest); shiftDays 0 -> all 0;
  drivers sorted by |impactDays| desc.
- `ForecastEngine`: one split random stream per run (so less scope / more capacity never makes a run finish
  later), plus public `windowStart(asOf)` / `windowEnd(asOf)` helpers. Frozen signatures unchanged.
- `mcp.AnalysisTools`: `explain_change`, `run_what_if`, `get_risks`, each through `ToolSupport.call` and
  `ProjectData.load`. run_what_if defaults targetDate to the release's releaseDate; unknown releases are a clear
  error listing the known ones. explain_change and get_risks add windowStart/windowEnd to their result so the meta
  block carries the data window.

**Verified**
- `ExplainEngineTest`: drivers sum to shiftDays over 40 random inputs (property test); scope and client-wait
  drivers with keys; adding Arjun's leave moves p85 later or equal and lists a leave driver ("Arjun 5"); zero shift
  -> zero impacts; inputs unchanged.
- `WhatIfEngineTest`: no change = plain forecast; drop by key or name; dropping an epic never lowers pByTarget
  (25 random inputs); adding people is faster, removing slower; inputs unchanged.
- `RiskEngineTest`: reasons, sides, ordering, bottleneck, fixture SHOP-106 client wait of 8 days.
- `AnalysisToolsTest` (`@SpringBootTest`, `ProjectData` mocked): tools registered; results and meta for all three.
- `mvnw.cmd -B verify` green locally.

**Known gaps / notes**
- Waits for `m4-e2e` before merging (rebase onto the real ToolSupport). Still to add then: the access-denied tool
  test with a token lacking SHOP, using Person A's security test helpers.
- The leave driver can only compare the absences visible now across the two forecast windows; the engine has no
  history of when a leave was entered. It is listed whenever the toDate window holds leave, even with 0 impact, so
  scenario 2 shows it. Check its impact during S4.
- If the shift and the weights point opposite ways the scaled impacts flip sign; the sentences stay factual.
