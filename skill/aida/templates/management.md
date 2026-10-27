# {project} {release} – management status, {asOf}

Format a `build_report(project, release, "management")` result. Use only values from the result. Keep it to one
screen.

## Headline
One sentence: on track / at risk / off track for {forecast.targetDate}, with **{forecast.pByTarget as %}** chance and
the p85 date **{forecast.p85}**.

## Forecast
| p50 | p85 (commit) | p95 | Chance by target |
| --- | --- | --- | --- |
| {forecast.p50} | {forecast.p85} | {forecast.p95} | {forecast.pByTarget as %} |

Basis: {forecast.sampleWeeks} weeks of throughput ({forecast.windowStart} to {forecast.windowEnd}),
{forecast.runs} runs, team capacity {forecast.capacityFactor}.

## Progress
{progress.done} of {progress.total} done, {progress.remaining} remaining.

## Why the date moved this week
For each of `topDrivers`: {detail} (**{impactDays} days**)

## Top risks
For each of `risks`: {key} – {reason}, {days} days ({side} side, {assignee})

## Decisions needed
Items waiting on the client (`asks`) and one or two options worth a `run_what_if` (drop an epic, add people, move
the date). Offer to run them.
