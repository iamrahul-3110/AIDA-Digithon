# M4 part b (A3) – Token security, MCP endpoint, audit (`m4-e2e` at SYNC S2, Person A)

Branch `a/m4-security` (merged as #9), based on main after #3 and #7 (`m2-adapters`; ForecastEngine and
QualityEngine merged). S2 recorded on `a/m4-s2`.

**Built**
- `security.TokenRegistry`: reads `aida.security.tokens-file` (a path, or `classpath:` in tests): a YAML list of
  `{name, sha256, projects, scopes}`. Hashes the presented token with SHA-256 and compares it with every entry using
  `MessageDigest.isEqual`. Re-reads the file when it changes; a missing file means no valid token (logged once).
- `security.BearerTokenFilter`: `Authorization: Bearer <token>` → `TokenPrincipal(name, projects, scopes)` with
  authorities `SCOPE_<scope>`. Added to the token chain only (not a servlet-wide filter).
- `security.SecurityConfig` (all chains stateless):
  - mock chain (profile `mock`): `/mock/**`, `/actuator/health` open.
  - token chain: `/mcp/**` needs `SCOPE_mcp`, `/admin/**` needs `SCOPE_admin`; CSRF off; async and error
    dispatches of an authorized request are allowed (the MCP stream uses them).
  - deny chain: everything else.
  - Missing or unknown token → 401 `{"error":"unauthorized"}` with `WWW-Authenticate: Bearer`; valid token without
    the scope → 403 `{"error":"forbidden"}`.
- Principal propagation: `ToolConfig` replaces Spring AI's `WebMvcStreamableServerTransportProvider` bean (same
  endpoint, JSON mapper and keep-alive settings) to add a context extractor that copies the request's
  `Authentication` into the MCP transport context. Every tool callback is wrapped to set it on the thread that runs
  the tool and restore the previous context afterwards.
- `mcp.ToolSupport` is real: no token or a project the token does not cover → audit line, then
  `ToolSupport.AccessDenied("Access denied: token '<name>' cannot read project <KEY>")`, which MCP returns as an error
  result (`isError: true`). Otherwise runs the body, adds `meta {tool, calledAt, windowStart, windowEnd}`, writes one
  audit line. `ToolSupport.currentPrincipal()` is public for tools that need the caller.
- `mcp.AuditLog`: JSON lines in `data/audit.log` `{ts, tokenName, tool, args, resultSummary}`; the summary is the
  result's top-level scalar fields (capped at 300 chars), or the access-denied / error message.
- `mcp.ForecastTools` (`AidaToolSet`): `forecast_delivery(project, release, asOf?, targetDate?)` (targetDate defaults
  to the release date; unknown release → error listing the known ones) and `check_data_quality(project)`. Both go
  through `ToolSupport.call` and `ProjectData.load`. A `Clock` bean supplies "today".
- `scripts/new-token.sh <name> <projects> <scopes>`: prints a random 32-byte hex token once and the `tokens.yml` entry
  with its SHA-256. `tokens.example.yml` with placeholder hashes. `tokens.yml` stays git-ignored.

**Verified**
- `mvnw.cmd -B verify` after rebasing onto main (with Person B's engines) → `Tests run: 215, Failures: 0, Errors: 0,
  Skipped: 0`, `BUILD SUCCESS`, no security errors in the log.
- `TokenSecurityTest` (MockMvc): no token → 401 JSON, unknown token or `Basic` scheme → 401, token without `mcp` → 403,
  `/admin/**` needs `admin`, other routes denied, registry matches by hash only (the hash itself is not a token).
- `McpEndToEndTest` (real Tomcat on port 18081, MCP Java SDK client with a bearer header): valid token →
  `forecast_delivery` JSON with meta, and the engine received the real SHOP data from the mock over the HTTP adapters;
  OPS-only token asking for SHOP → `isError` with "Access denied: token 'test-ops-only' cannot read project SHOP";
  `check_data_quality`; unknown release → tool error; tools listed; audit log has the token name and never a token
  value; a wrong token cannot initialize. Engines are Mockito beans in this test.
- `PrincipalPropagationTest`: a tool called on a separate thread pool, with only the MCP transport context, sees the
  caller (`pool-caller`); the project check still denies an OPS-only caller there; without the context the tool is
  denied; the pool thread's context is cleared afterwards.
- `bash scripts/new-token.sh demo SHOP,ops mcp,ADMIN` prints the entry with `projects: [SHOP, OPS]`,
  `scopes: [mcp, admin]`.
- Test tokens (`src/test/resources/tokens-test.yml`, plain values in `com.aida.security.TestTokens`) are test-only.

**SYNC S2 – first end-to-end answer (2026-10-27, main at `8fdc63c`, tagged `m4-e2e`)**
- Setup: `main` after #9 (A3) and #7 (Person B's ForecastEngine and QualityEngine); `mvnw.cmd -B verify` →
  `Tests run: 215, Failures: 0`. App started with `mvnw.cmd spring-boot:run` (profile mock, data for 2026-10-27).
  Token `lana-claude-code` (SHOP, OPS; scope mcp) created with `scripts/new-token.sh`; only its hash is in the
  git-ignored `tokens.yml`.
- Protocol check (JSON-RPC over `/mcp`): no token → 401 `{"error":"unauthorized"}`; with the token, `initialize` returns
  a session, `forecast_delivery` SHOP 3.2 → 18 remaining, p50 2026-11-21, p85 2026-11-24, p95 2026-11-25,
  target 2026-11-21, pByTarget 0.6282, 12 sample weeks (2026-08-03 to 2026-10-25), capacityFactor 0.953;
  `check_data_quality` SHOP → 99, allowed; OPS → 54, not allowed (stale_status 6, missing_estimate 13, bulk_close 12).
- Claude Code 2.1.311, with a project-style MCP config reading the header from `${AIDA_TOKEN}` (not `claude mcp add`,
  which would store the token in Claude's settings):
  `claude -p "Use the aida tools: will SHOP release 3.2 ship by its target date?" --mcp-config aida-mcp.json
  --strict-mcp-config --allowedTools "mcp__aida__forecast_delivery,mcp__aida__check_data_quality"`.
  Claude called `check_data_quality` and `forecast_delivery`; both appear in `data/audit.log` under
  `lana-claude-code`. Claude's answer, verbatim:

> **Probably, but it's close to a coin flip. AIDA puts the chance of shipping by the target date at 63%.**
>
> | | Date |
> |---|---|
> | Target (planned date) | **2026-11-21** |
> | p50 (50% likely done by) | 2026-11-21 |
> | p85 | 2026-11-24 |
> | p95 | 2026-11-25 |
>
> - **Remaining work:** 18 items.
> - **Basis:** 10,000 simulation runs using 12 weeks of throughput (2026-08-03 to 2026-10-25). The forecast already
>   accounts for planned leave and holidays, which cut capacity to 95.3% of normal.
> - **Data quality:** 99/100, so the data is fine to forecast from. Only two stories are missing estimates
>   (SHOP-21, SHOP-7).
>
> **What this means:** The target date is the median forecast, so there's roughly a 1-in-3 chance of missing it. If it
> does slip, it's likely to be small: 85% of runs finish by Nov 24 and 95% by Nov 25. The target is a Saturday, so in
> practice the deadline may be the end of Friday, Nov 20, which would be a bit tighter than the 63% suggests.
>
> **Options:**
> - **Hold the date:** cut 2 or 3 of the 18 items.
> - **Move the date:** commit to Nov 24 or 25 for high confidence.

- Every number in the answer matches the tool results above.

**Known gaps**
- Demo scenario 1 wants pByTarget 0.35-0.55 and p85 7-14 days after target; S2 gives 0.63 and 3 days. That is for
  SYNC S4: generator parameters (Person A) or engine rules (Person B), with the seed fixed.
- The generator puts the 3.2 target on asOf + 25 days, which lands on a Saturday for asOf 2026-10-27 (Claude noticed);
  S4 can move it to the nearest Friday.
- Overriding the transport bean means a future Spring AI change to its default transport settings has to be copied
  into `ToolConfig`.
