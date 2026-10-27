# {project} {release} – status update, {asOf}

Format a `build_report(project, release, "client")` result. Use only values from the result. Never name team
members (the result has none; do not add any), never mention internal (team-side) problems, and keep a
courteous, factual tone.

## Where we are
{progress.done} of {progress.total} items are complete; {progress.remaining} remain.

## Delivery outlook
Our planning date is **{forecast.p85}** (85% confidence), against the target of {forecast.targetDate}
({forecast.pByTarget as %} likely by then).

## What changed since last week
For each of `topDrivers`: {detail}

## What we need from you
For each of `asks`: **{key} {summary}** – waiting for your UAT for {daysWaiting} days.
If `asks` is empty: "Nothing is waiting on you this week."

## Items waiting on your side
For each of `risks`: {key} {summary} – {days} days
