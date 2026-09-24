# 12. Home mood from today's usage and nudges

## Context

The home screen showed the mascot in a fixed mood, with a debug picker to preview the others. Usage now lands in
`daily_stats` (see [0009](0009-daily-stats-aggregation.md)) and every nudge leaves `nudge_shown`, `nudge_response`
and `nudge_outcome` events (see [0010](0010-rule-based-nudges.md)), so the home screen can reflect the actual day.

## Decision

- **The screen reads Room directly and follows it.** `HomeViewModel` observes today's `daily_stats` rows and today's
  `nudge_shown`, `nudge_response` and `nudge_outcome` events. Room re-emits whenever the pipeline writes, so the
  screen updates while it is open, and it switches to the new day at midnight. Suppressed decisions are not read:
  the user never saw them. A new `EventDao` query filters by event type, so the screen does not re-read every
  foreground change of the day.
- **Usage counts watched apps only**, from `daily_stats`, with the same `WatchedApps` as the rules. Time in a
  browser or a messaging app is not what Nudgi is about. `daily_stats` only holds closed sessions, so a session
  still running is not counted yet.
- **The mood is a pure function of two numbers**: watched usage today and the nudges the user kept going after,
  i.e. whose outcome shows they did not leave the app.

  | Mood | When |
  |---|---|
  | Worried | 2 h of watched apps, or 2 nudges kept going after |
  | Neutral | 1 h of watched apps, or 1 nudge kept going after |
  | Happy | Otherwise |

  A nudge the user acted on is a good sign, not a bad one, and a nudge still inside its outcome window is not
  known yet; neither weighs on the mood. The usage thresholds are totals across watched apps, unlike the per-app
  `daily_budget` thresholds, so they live in `feature:home` rather than in `NudgeConfig`.
- **Each nudge is listed with its outcome** ("Took a break", "Kept going", nothing while pending) rather than the
  notification button tapped, for the reason given in 0010: the outcome measures what changed.
- **The app label lookup moved to `core:nudge` as `AppLabels`**, shared by the notification and the home screen.
- The debug mood picker stays, with a "Live" option to go back to the derived mood.

## Consequences

- The mood is itself something the user sees and may react to. It is part of the intervention, and the data
  collected from now on includes its effect. If the bandit is ever to separate the nudge's effect from the
  mood's, the mood shown will have to be logged too.
- Outcomes are written by the pipeline, not by the screen, so "Kept going" appears only once an evaluation has
  run after the outcome window closed: up to 15 minutes later when no watched app is in front.
- The thresholds are guesses. Real data from the Nothing Phone (1) should tell whether a typical day reads as
  happy, and they should be revisited then.
