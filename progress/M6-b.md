# M6b – Reports and skill (Person B, part of `m6-guardrails`)

**Built**
- `engine.ReportEngine.build` -> `{audience, project, release, asOf, forecast, progress {done, remaining, total},
  topDrivers, risks, asks}`: topDrivers = explain over the last 7 days, top 3; risks = top 5 for the release's
  items; asks = release items waiting on the client (key, summary, status, daysWaiting).
  - team, management: everything (management adds nothing hidden from team).
  - hq: only `audience, project, release, asOf, forecast, progress, topDrivers` (`ReportEngine.HQ_KEYS`).
  - client: client-side risks only, no `assignee` field, and every team member or assignee name in any string
    (driver sentences, summaries) replaced with "a team member" (whole-word, case-insensitive).
  - Unknown audience -> IllegalArgumentException listing the four.
- `mcp.ReportTools.build_report(project, release, audience)` through `ToolSupport.call` and `ProjectData.load`;
  replaces the report's forecast with one targeting the release's releaseDate (the engine has no release dates)
  and adds windowStart/windowEnd for the meta block.
- `skill/aida/SKILL.md` (`name: aida-delivery-analyst`): tool map; every number from a tool; check data quality
  first and explain the problems instead of a date when forecastAllowed is false; state data window, sample size
  and confidence levels; explain_change drivers as returned; offer what-ifs; verify_answer before replying;
  templates for status reports; access-denied handling.
- `skill/aida/templates/{team,management,client,hq}.md` formatting a build_report result per audience.

**Verified**
- `ReportEngineTest`: team has every key, progress, ≤3 drivers, 5 risks, 3 asks; management equals team; client
  JSON contains none of the SHOP or OPS team names (also from summaries) and only client-side risks; OPS client
  report scrubbed; hq has exactly the allowed keys; unknown audience rejected; scrubbing is whole-word.
- `ReportToolsTest` (through Person A's real `ToolSupport`, calls run as a `TestTokens` authentication): tool
  registered; forecast targets the release date; client result has no team names; hq has no risks or asks; a token
  covering only OPS gets "Access denied: token 'test-ops-only' cannot read project SHOP" and no data is loaded.
- `mvnw.cmd -B verify` green locally.

**Known gaps / notes**
- Rebased onto main after M5b (#11) and Person A's Guardrail (#12); ReportTools follows ForecastTools (injected
  `Clock`, upper-cased project key) and shares AnalysisTools' release check. `mvnw.cmd -B verify`: 263 tests, all
  green. With #12 already merged, this PR completes `m6-guardrails`; tag it after the merge.
- Names are scrubbed from the client report only; team, management and hq driver sentences keep names (the hq
  template uses driver types and impacts only).
