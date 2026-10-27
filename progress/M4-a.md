# M4 part b (A3) – Token security, MCP endpoint, audit (`m4-e2e` at SYNC S2, Person A)

Branch `a/m4-security`, stacked on `a/m2-adapters` (PR #3).

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
- `mvnw.cmd -B verify` → `Tests run: 182, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, no security errors
  in the log.
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

**Known gaps**
- SYNC S2 (Claude Code answering "Will SHOP 3.2 ship on time?" against the real engines) runs after this PR and
  Person B's ForecastEngine and QualityEngine are on main; its answer is added here and `m4-e2e` is tagged then.
- Overriding the transport bean means a future Spring AI change to its default transport settings has to be copied
  into `ToolConfig`.
