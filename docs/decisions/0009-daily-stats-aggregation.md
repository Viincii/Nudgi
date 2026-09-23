# 9. Daily stats aggregation

## Context

`core:usagestats` writes `app_foreground` / `app_background` rows to `events` every 15 minutes (see
[0008](0008-usage-stats-polling.md)). The `daily_stats` table from [0006](0006-events-and-daily-stats-schema.md)
exists so the UI and the rules can read per-app daily totals without scanning raw events, but nothing filled it.

While building the aggregation, a gap in 0008 turned out to matter: a background event only got a duration when
its foreground event was in the same poll window, so any session spanning a 15-minute boundary was recorded with
zero duration. Those long sessions are exactly the doom-scrolling this app is about.

## Decision

- **Durations are fixed at the source.** The poller re-reads a six-hour lookback before its window, only to pair
  background events with the foreground event that opened them, and still inserts only events inside the window.
  `events.duration_ms` is then correct for both the aggregation and the later bandit, and the pairing logic stays
  in one place (`mapToEventEntities`). Sessions longer than the lookback still report zero, and in practice mean
  the phone was left on an app.
- **The aggregation runs inside the polling worker**, right after a successful poll, instead of as a second
  periodic job. New events only ever come from the poll, so this keeps `daily_stats` in step with `events`
  without a second schedule to reason about.
- **Days are recomputed, not incremented.** A session is rebuilt from each `app_background` event as
  `[timestamp - duration_ms, timestamp)`. Every local calendar day touched by a session closed during the poll
  window is recomputed from all the sessions overlapping it, then upserted. Recomputing makes the job
  idempotent: a retried or duplicated run cannot double-count.
- **Sessions crossing midnight are split** between the two days. Days come from the device's current time zone,
  so a DST day is 23 or 25 hours long.
- **Only closed sessions count.** An app still in the foreground at poll time is counted once it goes to the
  background.
- `nudge_count` is written as 0: nudges are not recorded as events yet.
- The aggregation lives in `core:usagestats` rather than a new module, since it depends on that module's event
  types and on nothing else.

## Consequences

- Today's `daily_stats` lag real usage by up to one poll interval plus the length of the running session.
- If the aggregation fails after a poll has advanced its window, the affected days are only corrected when a
  later poll touches them again. A day that ends in between keeps stale totals. That is acceptable while nothing
  reads `daily_stats` yet; an "aggregated until" watermark would close the gap if it turns out to matter.
- A time zone change rewrites future days in the new zone but leaves past rows as they were computed.
- Once nudge events exist, the aggregation has to count them too. Otherwise its upserts keep resetting
  `nudge_count` to 0.
