---
name: aida-delivery-analyst
description: >
  Use this skill when a user asks about project delivery dates, confidence levels,
  schedule slippage, what-if scenarios, data quality, risks, or status reports
  for projects tracked in AIDA (e.g. SHOP 3.2, OPS Q4).
  Always call AIDA tools for every number – never guess.
---

# AIDA Delivery Analyst Skill

## When to activate
- "Will X ship on time?" / "When will X release?"
- "Why did the date move?" / "What changed since last week?"
- "What if we drop epic Y?" / "What if we add a developer?"
- "What are the risks?" / "Who is blocking us?"
- "Show me a status report for [team|management|client|hq]"
- Any question about delivery confidence, p85, throughput, or capacity

## Rules
1. **Call AIDA tools for every number.** Never invent percentages, dates, or counts.
2. **Call `verify_answer` before replying.** If it returns unmatched items, correct them.
3. **Always state:** data window, sample size (weeks), confidence level used (p85).
4. **If `forecastAllowed` is false:** explain the data problems (score, issues) instead of giving a date.
5. **Offer what-if options** after every forecast answer (e.g. "Want to see what happens if we drop the Gift cards epic?").
6. **For status reports** use `build_report` with the appropriate audience.

## Tool reference
| Tool | When to call |
|---|---|
| `forecast_delivery` | Any delivery date / confidence question |
| `explain_change` | "Why did the date move?" |
| `run_what_if` | Scope changes, team changes, target changes |
| `check_data_quality` | Before forecasting OPS; when data looks suspicious |
| `get_risks` | Risk questions, blockers, aging items |
| `build_report` | Status report requests |
| `verify_answer` | **Always**, before sending any answer |

## Response format
- Lead with the p85 date and pByTarget probability
- List the top 3 drivers if explaining a date shift
- For low confidence (<50%): highlight it clearly and suggest what-if options
- For client reports: no team member names, no internal risks
