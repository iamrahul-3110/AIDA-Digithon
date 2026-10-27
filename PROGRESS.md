# AIDA MVP – Progress

Milestones are committed and tagged on branch `lana-solo-mvp` of `iamrahul-3110/AIDA-Digithon`.

## M0 – Skeleton, contracts, CI (`m0-skeleton`)

**Built**
- Maven project with wrapper (Maven 3.9.11), Java 21.
- Versions: Spring Boot **4.0.9** with the Spring AI **2.0.2** BOM. They resolved together, so the
  3.5.x / 1.1.x fallback was not needed. Jackson is 3.x (`tools.jackson`), MCP Java SDK 2.1.0.
- Dependencies: web, security, validation, actuator, `spring-ai-starter-mcp-server-webmvc`, test, security-test.
- `application.yml`: port 8080, MCP server `aida` 0.1.0 with STREAMABLE protocol at `/mcp`, AIDA settings
  (jira/leave base URLs, tokens file, data dir, alerts), profile `mock` on by default.
- All contract records in `com.aida.contract` plus a `Statuses` helper (workflow order, active, client-side).
- Fixtures in `src/test/resources/fixtures`: 15 SHOP issues (every status, epics SHOP-E5 and SHOP-E7, one story
  without an estimate, SHOP-106 in Waiting for Client UAT for 8 days at 2026-10-27), the 6-person SHOP team,
  2 leaves and 2 public holidays.
- Tests: JSON round trip for every record, ISO date output and field names (`ContractJsonTest`), fixture shape
  (`FixturesTest`), context load plus an ISO-date check on the application's mapper (`AidaApplicationTests`).
- `.github/workflows/ci.yml`: checkout, Temurin 21 with Maven cache, `./mvnw -B verify` on every push and PR.

**Verified**
- `mvnw.cmd -B verify` → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

**Known gaps / notes**
- The local network intercepts TLS. Java needs `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, and
  Git Bash's curl fails the revocation check on the intercepting CA, so locally the build runs via `mvnw.cmd`
  (PowerShell download). CI on GitHub's Linux runners uses `./mvnw` directly.
- Spring Security still uses its generated default user; the bearer-token security lands in M4.
- `gh` is installed but not logged in yet, so the CI run is checked on GitHub's Actions page instead.
