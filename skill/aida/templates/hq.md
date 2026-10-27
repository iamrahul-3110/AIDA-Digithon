# {project} {release} – HQ summary, {asOf}

Format a `build_report(project, release, "hq")` result: forecast, progress and the top drivers only. Five lines at
most, no item lists, no names.

- **Outlook:** p85 {forecast.p85} vs target {forecast.targetDate} – {forecast.pByTarget as %} chance by target
  (p50 {forecast.p50}, p95 {forecast.p95}).
- **Progress:** {progress.done}/{progress.total} done, {progress.remaining} remaining.
- **Drivers:** for each of `topDrivers`, "{type} {impactDays} days" separated by semicolons.
- **Basis:** {forecast.sampleWeeks} weeks of throughput ({forecast.windowStart} to {forecast.windowEnd}),
  {forecast.runs} runs.
