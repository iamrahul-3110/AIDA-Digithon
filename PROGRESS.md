# PROGRESS

## M0 – Skeleton, Contracts, CI
**Built:** Maven project (Spring Boot 3.5 + Spring AI 1.0.0 MCP server), Java 21, all contract records,
BearerTokenFilter/SecurityConfig, application.yml, GitHub Actions CI workflow, README.  
**Verified:** `./mvnw verify` passes; ContractSerializationTest covers all records with JSON round-trips.  
**Stack note:** Using Spring Boot 3.5.x with Spring AI 1.0.x (compatible versions; BOM imported).  
**Known gaps:** No tokens.yml yet (file is git-ignored; use `scripts/new-token.sh` to create one).

## M1 – Mock Data and Endpoints
**Built:** DataGenerator (seed=7, ~180 SHOP issues, ~60 OPS issues), MockJiraController, MockCalendarController,
MockAdminController, leave console HTML, planted demo scenarios (scope +18%, UAT wait, Gift cards epic,
Arjun in-progress items, OPS bulk close + stale items).  
**Verified:** MockEndpointsTest covers search (Jira shape, fixVersion filter), versions, team, calendar add/delete, reset.  
**Known gaps:** Mock data uses today as asOf so absolute dates shift each run.

## M2 – Adapters and Snapshots
**Built:** HttpJiraSource (paged, Jira-shaped JSON parsing), HttpLeaveSource, Snapshots engine.  
**Verified:** SnapshotTest proves resolved-after-d returns unresolved at d with correct status.

## M3 – Capacity and Forecast
**Built:** Capacity.byWeek (leave/holiday aware), ForecastEngine (Monte Carlo, SplittableRandom seed=42),
p50/p85/p95/pByTarget, capacityFactor.  
**Verified:** ForecastEngineTest: deterministic, more leave ≥ same p85, earlier target ≤ same pByTarget, 10k runs < 1s.

## M4 – Token Security and MCP Endpoint
**Built:** TokenRegistry (SHA-256 hashing, constant-time compare), BearerTokenFilter, SecurityConfig,
QualityEngine, AuditLog, AidaTools (forecast_delivery + check_data_quality), ToolConfig.  
**Verified:** SecurityTest: no token→401, unknown token→401, /mock accessible without token.  
**Known gaps:** Full MCP protocol test (initialize + tools/call) pending token setup on CI.

## M5 – Explanations, What-Ifs, Risks
**Built:** ExplainEngine (scope_added, client_wait, leave, throughput_drop, aging drivers scaled to shiftDays),
WhatIfEngine (immutable input copy), RiskEngine (aging/client_wait/stale, bottleneckStage).
Tools: explain_change, run_what_if, get_risks.

## M6 – Reports and Guardrail
**Built:** ReportEngine (4 audiences: team, management, client, hq; client strips names/internal risks/adds asks),
Guardrail.verify (extracts percentages, checks against audit entries), verify_answer tool.

## M7 – Alerts and Demo
**Built:** AlertService (@Scheduled, pByTarget drop ≥15% or p85 shift ≥5 days triggers alert, webhook POST,
alerts.log, alert-state.json), AlertController (/admin/alerts/run, SCOPE_admin).

## M8 – Claude Setup, Skill, Release
**Built:** skill/aida/SKILL.md, claude/mcp.json.example, claude/claude_desktop_config.example.json,
scripts/new-token.sh, tokens.example.yml, README with full demo script.
