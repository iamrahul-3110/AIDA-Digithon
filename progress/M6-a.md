# M6 part b (A4) – Guardrail (`m6-guardrails` once B5 ReportEngine is also merged, Person A)

Branch `a/m6-guardrail`, from main at `8fdc63c` (`m4-e2e`).

**Built**
- `AuditLog` keeps each token's full tool results in memory for 30 minutes (`remember`, `recentResults`), next to the
  audit lines. `ToolSupport` remembers every successful result except `verify_answer`'s own, so a flagged value can
  never become a source by being echoed back.
- `Guardrail.verifyAnswer(tokenName, draft)` → `{ok, unmatched}`:
  - Extracts dates first (`2026-12-15`, `Dec 15`, `Dec. 15`, `December 15th`, `15 December`, `December 15, 2026`),
    then numbers (`18`, `10,000`, `95.3`) and percentages (`63%`, `45 percent`).
  - Sources: every value in that token's results from the last 30 minutes (numbers, ISO dates and numbers inside
    strings such as quality detail sentences, digits in field names like p85, years of result dates); the `meta`
    block is not a source.
  - Normalisation: a percentage matches a fraction (45% = 0.45) or the same number; a value matches at the precision
    the draft shows (63% matches 0.6282, 95.3% matches 0.953, 45.4% does not match 0.45); a date without a year
    matches this year or next.
  - Not values: issue keys (SHOP-21) and labels (p85). `unmatched` lists the draft's own text in reading order.
- `GuardrailTools.verify_answer(draft)`: goes through `ToolSupport.call` (no project, so any valid token may call it;
  the audit line records the draft's length, not its text) and checks against the caller's own results only.

**Verified**
- `mvnw.cmd -B verify` → `Tests run: 230, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- `GuardrailTest` (12): a draft built from a forecast result passes; an invented `91%` is flagged; invented numbers
  and dates are flagged; percentage and precision rules; every date form; a year-less date in next year; issue keys,
  labels and years are ignored; numbers from quality detail sentences count; another token's results do not count;
  results expire after 30 minutes; meta is not a source; a draft without values passes.
- `GuardrailEndToEndTest` (MCP SDK client, real ForecastEngine on the mock data over HTTP): a draft built from a real
  `forecast_delivery` result passes; replacing its percentage with an invented one returns
  `{"ok":false,"unmatched":["<n>%"]}`, and asking again does not whitelist it; the same draft from the OPS-only token
  (no results of its own) fails; `verify_answer` is listed.

**Known gaps**
- Strict by design: numbers Claude derives itself ("1-in-3", "2 or 3 items", the weekday before a Saturday target)
  are flagged. The skill (Person B, B5) should tell Claude to rephrase or drop flagged values.
- Results live in memory only, so a restart forgets them; drafts checked after a restart need fresh tool calls.
- `m6-guardrails` is tagged by whoever merges second of this PR and B5 (ReportEngine).
