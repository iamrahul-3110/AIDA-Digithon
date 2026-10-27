# M1 (A1) – Mock data and endpoints (`m1-mock`, Person A)

Branch `a/m1-mock`, stacked on `a/m0-seams` (PR #1); rebased onto main once PR #1 is merged.

**Built**
- `DataGenerator.generate(asOf, seed)`: pure and deterministic (`SplittableRandom(seed)`), timestamps 09:00-18:00 IST
  so an instant's date is the same in IST and UTC; keys assigned in creation order like Jira.
  - SHOP: about 170 issues over 26+ weeks, 4-7 items Done in every full ISO week before asOf's week. Releases:
    3.1 released 8 weeks ago, 3.2 due asOf + 25 days, 3.3 six weeks later.
  - SHOP 3.2 planted: scope +18% in the last 14 days (new items created in that window); 3 stories in Waiting for
    Client UAT for 6-9 days; SHOP-E7 "Gift cards" with 5-6 open items; Arjun holding 5 in-progress items;
    no SHOP active item untouched for 14+ days (so SHOP's data quality stays high).
  - OPS (release Q4, due asOf + 60): 60 issues; 6 of 20 active items (30%) untouched for 21-45 days; every other
    story without an estimate; 12 items closed in the same minute 20 days before asOf.
  - Absences: hardcoded public holidays for IN, KR and VN (`DataGenerator.PUBLIC_HOLIDAYS`, commented that every
    date must be verified against official calendars), taken from 12 weeks before asOf to 3 months after; leaves for
    Priya, Ji-woo and Meera (none for Arjun, so demo scenario 2 adds his).
- `MockStore` (profile mock): generates on startup with seed `aida.mock.seed` (7) for today or `aida.mock.as-of`,
  keeps the data in memory, saves it to `data/mock-data.json` (git-ignored) after every change.
- Endpoints (profile mock): `GET /mock/jira/rest/api/2/search` (Jira shape with changelog, `project=KEY` with
  optional `AND fixVersion="NAME"`, pagination, `maxResults` capped at 100, other JQL → 400),
  `GET /mock/jira/rest/api/2/project/{key}/versions`, `GET /mock/team?project=`,
  `GET/POST /mock/calendar/events`, `DELETE /mock/calendar/events/{id}`, `POST /mock/admin/reset`.
- `GET /mock/console.html`: leave console (plain HTML + fetch): add leave, list/delete events, reset.
- `SecurityConfig`: a mock-profile chain that permits `/mock/**` and `/actuator/health` (CSRF off), and a default
  chain that denies everything else until the token security lands in A3.

**Verified**
- `mvnw.cmd -B verify` → `Tests run: 157, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - `DataGeneratorTest` (15 checks × 8 asOf dates covering every weekday): same asOf + seed gives identical data,
    histories consistent, every planted target above.
  - `MockEndpointsTest` (MockMvc through the real security chain): search shape and field mapping, pagination,
    JQL filter, versions, team, calendar add/list/delete and validation, reset, console page, `/mcp` denied.
- Ran the app for asOf 2026-10-27: SHOP 169 issues, 3.2 has 53 items with 18 remaining (In Progress 6, In Review 2,
  QA 2, UAT 3, To Do 5), scope +17.8% in 14 days, Gift cards 5 open, Arjun 5 in progress; OPS 60.
  Added a leave for Arjun in the console: `Added lv-15: Arjun, 2026-10-27 to 2026-10-27.`, and it showed in the
  calendar.

**Known gaps**
- 3.2 remaining is 18-22 depending on asOf (the target says about 20); the forecast calibration (B2) and demo
  tuning (S4) may adjust generator parameters, with the seed fixed.
- The holiday list covers September 2026 to January 2027; extend it if the demo date moves past that.
- `/mcp` is denied until A3 adds the bearer-token chain.
