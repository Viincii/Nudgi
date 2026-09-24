# 13. Home screen widget

## Context

Roadmap step 3: Nudgi on the home screen, so the day's mood is visible without opening the app. The mood is
derived from today's usage and nudges (see [0012](0012-home-mood-from-today.md)), and that derivation lived in
`feature:home`. A feature module never depends on another one, and Glance widgets cannot host a Compose `Canvas`,
which is how the mascot is drawn (see [0002](0002-mascot-drawn-with-compose-primitives.md)).

## Decision

- **The day's summary moves to a new `core:today` module.** `TodayRepository` follows today's `daily_stats` and
  nudge events and derives the mood, with the thresholds of 0012 unchanged. The home screen and the widget both
  read it, so they cannot disagree. `HomeViewModel` keeps only the debug mood override, which the widget ignores.
- **The mascot is rendered to a bitmap from the same drawing.** `renderMascot` runs the `DrawScope` code of
  `NudgiMascot` on a bitmap, at rest with open eyes. There is no per-mood asset to keep in sync with the code.
  The bitmap has a fixed size (320 px) because widget bitmaps travel through a binder transaction capped at about
  1 MB.
- **A Glance widget in `feature:widget`**: the mascot, plus today's watched usage when the widget is tall enough
  (the mascot alone below 100 dp of height). It defaults to 2x2, resizes down to 1x1, and opens the app on tap.
  Colors come from `GlanceTheme`, i.e. the wallpaper's dynamic colors.
- **The widget shows a snapshot and the pipeline refreshes it.** A Glance session does not stay alive to follow a
  `Flow`, so the widget reads the day once per update. `UsagePipeline` redraws every placed widget after each
  run: runs are the only writers of usage and nudge outcomes, and the periodic worker also carries the widget over
  midnight. The provider declares no `updatePeriodMillis`.

## Consequences

- The widget can lag behind the home screen by up to one pipeline run, i.e. 15 minutes when no watched app is in
  front, which is also how late "Kept going" appears on the home screen (see 0012). It may lag a little longer
  after midnight, until the first periodic run of the new day.
- The widget adds a second surface where the user sees the mood, and one they see far more often than the app.
  What 0012 says about logging the mood shown applies even more now.
- No generated preview in the widget picker yet: the launcher shows the app icon. Android 15's generated previews
  could reuse `renderMascot`.
- Per-mood bitmaps are also what a lock screen widget or a notification icon would need later.
