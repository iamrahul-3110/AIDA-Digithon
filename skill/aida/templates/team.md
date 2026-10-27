# {project} {release} – team status, {asOf}

Format a `build_report(project, release, "team")` result. Use only values from the result; leave out a section
whose list is empty (say "none" instead).

## Forecast
- Chance of shipping by {forecast.targetDate}: **{forecast.pByTarget as %}**
- p50 {forecast.p50} · **p85 {forecast.p85}** · p95 {forecast.p95}
- Based on {forecast.sampleWeeks} weeks of throughput ({forecast.windowStart} to {forecast.windowEnd}),
  {forecast.runs} runs, capacity {forecast.capacityFactor}

## Progress
{progress.done} done · {progress.remaining} remaining · {progress.total} in scope

## What moved the date this week
For each of `topDrivers`: **{type}** ({impactDays} days) – {detail} ({keys})

## Risks
| Item | Summary | Reason | Days | Owner |
| --- | --- | --- | --- | --- |
| {key} | {summary} | {reason} | {days} | {assignee} |

## Waiting on the client
For each of `asks`: {key} {summary} – {daysWaiting} days in {status}

## Next steps
Two or three concrete actions that follow from the risks and drivers above (for example: chase the client on the
oldest UAT item, rebalance the owner holding the most stale items). No new numbers.
