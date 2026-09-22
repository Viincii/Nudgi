# 6. `events` and `daily_stats` Room schema

## Context

The rule-based V1 needs somewhere to record what it observes, and the later on-device contextual bandit needs
enough raw history to reconstruct context, the action taken (a nudge, or none) and its outcome. `core:database`
existed only as an empty module skeleton with the Room convention plugin wired in.

## Decision

Two Room entities, matching the two-layer model already described in `CLAUDE.md`:

- `events`: raw, append-only rows — `id, timestamp, event_type, package_name, duration_ms, metadata`.
  `event_type` is a plain string rather than an enum, since the concrete set of event types belongs to the
  usage-tracking and nudging work that hasn't been built yet; adding new values later is a free-form string
  insert, not a migration. `package_name` is nullable for events that aren't scoped to one app. `metadata` is a
  JSON blob for whatever event-specific context doesn't warrant its own column.
- `daily_stats`: one row per `(date, package_name)`, holding `usage_ms` and `nudge_count`. `date` is an
  ISO-8601 `yyyy-MM-dd` string for the device-local calendar day. Refreshed by a WorkManager job that
  aggregates `events` rows (not built yet).

Schemas are exported to `core/database/schemas/` and committed, per `CLAUDE.md`.

DAO tests run as JVM unit tests (`testDebugUnitTest`, matching CI) via Robolectric, since Room's Android builder
requires a real `Context` to open even an in-memory database — there's no context-free JVM path on the Android
target. `androidx-test-core`'s `ApplicationProvider` supplies that context under Robolectric.

## Consequences

- `core:database` now pulls in `robolectric` and `androidx-test-core` as `testImplementation`, and
  `unitTests.isIncludeAndroidResources` is turned on for the module so Robolectric can initialize.
- No Hilt wiring yet: nothing depends on `core:database` yet. The `NudgiDatabase`/DAO providers land with the
  `UsageStatsManager` polling work that actually consumes them.
- The `event_type` taxonomy and any `daily_stats` migrations are still open; this schema is expected to evolve
  once usage tracking is implemented, with no released data to migrate around yet.
