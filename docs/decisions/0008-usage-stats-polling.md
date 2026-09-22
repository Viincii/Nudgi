# 8. Usage stats polling

## Context

The `events` schema and the guided permission onboarding (see [0006](0006-events-and-daily-stats-schema.md) and
[0007](0007-guided-permission-onboarding.md)) are both in place; the next roadmap step is actually reading
`UsageStatsManager`. The stack mandates periodic polling rather than logging every foreground/background event
as it happens, so a recurring background job is needed, and it has to survive process death between app
launches.

## Decision

- New `core:usagestats` module. `UsageStatsManager.queryEvents()` only reports `ACTIVITY_RESUMED` /
  `ACTIVITY_PAUSED` transitions since the last poll, so the module owns both reading them and turning them into
  `EventEntity` rows — nothing else needs raw usage events, and the mapping is the part worth unit-testing.
  `isUsageAccessGranted()` moved here from `feature:onboarding`, as anticipated in 0007: the poller needs it too,
  and a `core` module is the natural place both `feature:onboarding` and the poller can depend on.
- `WorkManager` (`PeriodicWorkRequest`, 15-minute interval — its minimum) runs a `@HiltWorker` that delegates to
  `UsageStatsPoller`. The poller is plain, injectable, and Android-free apart from its three collaborator
  interfaces (`UsageEventsSource`, `UsagePollState`, `UsageAccessPermissionChecker`), so its windowing and
  no-op-without-permission logic run as fast JVM tests with fakes, without Robolectric.
- The poll window is `[last polled until, now)`, persisted in `SharedPreferences` so it survives process death;
  a first run (or one after data was cleared) looks back a fixed 15 minutes instead of querying since epoch, to
  avoid a large backfill.
- `NudgiApplication` implements `Configuration.Provider` and schedules the periodic work unconditionally in
  `onCreate()` (`ExistingPeriodicWorkPolicy.KEEP`, so re-scheduling on every launch is a no-op once it's running).
  The poller itself checks usage access and does nothing without it, so scheduling doesn't need to wait for
  onboarding to complete. The manifest disables `WorkManagerInitializer`'s default startup (`tools:node="remove"`
  on the `androidx.work.WorkManagerInitializer` meta-data, not the old `androidx.work.impl.*` name) so the worker
  gets Hilt-injected dependencies instead of a no-arg constructor.
- A background event's `duration_ms` is the session length, computed by pairing it with the most recent
  foreground event seen for the same package in the same poll; a background event with no matching foreground
  (already running when the window opened) reports zero rather than guessing when it started.

## Consequences

- `core:database` now depends on Hilt (a `DatabaseModule` provides the singleton `NudgiDatabase` and its DAOs),
  the first core module to do so; `EventDao` gained `insertAll()` for batched writes per poll.
- Real data collection only starts once the app is installed on-device with usage access granted — the debug
  build needs to be sideloaded onto the Nothing Phone (1) for the `events` table to see real rows.
- `metadata` is written as `"{}"` for now; enriching it (e.g. screen-on state, time of day) is deferred until the
  rule-based logic actually needs more context than timestamp, package and duration.
