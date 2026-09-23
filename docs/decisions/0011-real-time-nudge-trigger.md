# 11. Real-time nudge trigger

## Context

The rule-based nudges from [0010](0010-rule-based-nudges.md) are evaluated by the periodic worker, every 15 minutes
at best. A `long_session` nudge meant for minute 20 can then arrive anywhere up to minute 35, which is too late
for the one moment a nudge can help. The accessibility service was already granted during onboarding (see
[0007](0007-guided-permission-onboarding.md)) and did nothing yet.

## Decision

- **The accessibility service reports foreground changes, nothing more.** It reads `TYPE_WINDOW_STATE_CHANGED`
  and forwards the package name to a `ForegroundAppListener`. It still never reads screen content. The notification
  shade (`com.android.systemui`) and the default keyboard are skipped, because they open windows on top of an app
  without ending its session. `core:accessibility` only defines the listener interface, which `app` binds, so it
  keeps depending on no other module.
- **Real time means running the same pipeline sooner, not a second code path.** When a watched app comes to the
  foreground, `RealtimeNudgeTrigger` runs the whole pipeline: poll, aggregate, evaluate. The evaluation returns
  when a rule could next fire if the user stays (`nextEvaluationAt`), and the trigger sleeps until then and runs
  again, until the user leaves the app. The rules keep reading only `events`, so a nudge decided in real time and
  one decided by the worker cannot disagree, and nothing needs another source of truth for the current session.
- **Two seconds of settling before the first run.** Usage events reach `UsageStatsManager` slightly after the
  window change the accessibility service sees. Polling at once could close the poll window before the foreground
  event is recorded, and that event would never be read.
- **A pipeline run cannot be cancelled.** The trigger cancels its schedule whenever the user switches apps. A poll
  cancelled between inserting its events and saving its window would insert them again on the next run, so each
  run is `NonCancellable`, and runs stay serialized by the existing mutex.
- The periodic worker keeps running unchanged, for when the accessibility service is off: Android disables it
  when the app is force-stopped.

## Consequences

- A nudge fires within seconds of its threshold while the accessibility service runs, and within 15 minutes
  otherwise.
- Each app switch into a watched app costs one poll, which re-reads the 6-hour session lookback from
  `UsageStatsManager`. App switches happen at human pace, so this stays cheap, but it is the first thing to look
  at if battery use shows up.
- The schedule lives in memory. If the process dies while a watched app is open, the worker takes over until the
  next foreground change.
- The home screen is an ordinary foreground change: leaving a watched app for it stops the schedule.
- Testing tools that drive the UI through `UiAutomation` (`uiautomator dump`, or an agent's screen inspection)
  make Android unbind every other accessibility service while they are connected. On an emulator under such a
  tool, the service shows as enabled but not bound (`dumpsys accessibility`), and real-time nudges silently stop.
  This is not a bug in the app. Stop the tool to test the trigger.
