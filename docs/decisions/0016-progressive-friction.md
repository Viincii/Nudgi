# 16. Progressive friction on watched apps

## Context

Roadmap step 4: going beyond notifications when they stop working. Today a nudge is a notification with two buttons,
"I'll stop" and "5 more minutes" (see [0010](0010-rule-based-nudges.md)). A snooze brings a follow-up 5 minutes
later, and nothing prevents snoozing forever: the follow-up skips the cooldown and the holdout, and each follow-up
is only another notification.

Nudgi is also collecting data for an on-device contextual bandit. Any new way to intervene is a new action the
bandit will choose between, so it has to be recorded like the nudges are: context, action, propensity and outcome.
A friction that is not logged, or that is applied deterministically, would be invisible to the model, or impossible
to evaluate offline.

The accessibility service already runs and reports foreground changes (see
[0011](0011-real-time-nudge-trigger.md)). It can also draw a window over other apps
(`TYPE_ACCESSIBILITY_OVERLAY`) and send the user to the home screen (`GLOBAL_ACTION_HOME`), so none of this needs
a new permission.

## Decision

### Scope

Friction only applies to **watched apps**, the same set as the nudges (`WatchedApps`: social, video and news
categories plus the known feed apps). No separate list to maintain, and nothing outside the apps Nudgi already
watches.

### Escalation: two snoozes per level

The friction level of an app goes up after **two snoozes** at the current level. Proposed ladder:

| Level | Form | How the user carries on |
|---|---|---|
| 0 | Notification, as today | "5 more minutes" in the notification |
| 1 | Warning overlay over the app, with Nudgi worried | Tap "5 more minutes" |
| 2 | Same overlay, the button unlocks after a countdown (10 s) | Wait, then tap |
| 3 | Forced close: back to the home screen | Reopen the app, which starts again at level 2 |

The level an app has reached is derived from the events, not stored: it is the highest friction applied to a nudge
shown on it today. Held-out, paused and fallen-back nudges applied less, so they never raise it. A forced close is
one-off: after it the app is at level 2, and its snoozes are counted afresh. Reopening the app shows nothing by
itself; the next nudge comes when a rule fires, at level 2.

- Snoozes are counted **per app, since the start of the day**, not per session. A session ends after a minute
  away from the app (`sessionMergeGapMs`), so counting per session would let a quick app switch reset the ladder.
- The level only rises on a snooze follow-up, the moment the user already chose to keep going. The other rules
  (`long_session`, `daily_budget`, `late_night`) still fire at the level the app has reached, so a level-1 app gets
  overlays instead of notifications for the rest of the day.
- "I'll stop" never raises the level. A day with no snooze stays at level 0.
- The level resets at the start of the next day, and only then. A decay after a long break within the day would be
  one more parameter to tune with no data to tune it yet; it can be added later if the data shows levels staying
  high long after the user came back.

Level 2 stays between the overlay and the forced close: going straight from a tappable overlay to closing the app
is a brutal step, and an intermediate level is one more action whose effect can be measured.

### Pausing friction

Settings gain a "pause friction for 1 hour" button. While paused, watched apps get level 0 (notifications) whatever
their level, and the level itself is kept for when the pause ends. The pause is recorded as a `friction_paused`
event with its end time, and every nudge decision taken during it records that the pause capped its level.

Some days the user genuinely needs a feed app. Without a way out, the likely reaction to friction at the wrong
moment is to turn the accessibility service off, which stops friction and real-time nudges together and loses the
data. A logged pause keeps the user in control and keeps the data honest: a pause is itself a signal about the
friction that preceded it.

### Logging

Every friction decision is a nudge decision, recorded with the existing events rather than new event types, so the
contract of 0010 stays the one the bandit reads:

- `nudge_shown` and `nudge_suppressed` gain the **friction level** actually applied and the **level the rules asked
  for**, the escalation probability, and whether a pause capped the level. The two levels differ when escalation is
  held out (below), when friction is paused, or when the overlay could not be shown because the accessibility
  service was not running. In that case the nudge falls back to a notification and records why
  (`friction_fallback`). Friction is applied before the decision is recorded, so the row says what the user got.
- `nudge_response` keeps its values. The overlay's buttons are the notification's, "I'll stop" and "5 more minutes",
  and record `stop` and `snooze`: a snooze on an overlay has to bring its follow-up and count toward the next level
  exactly like one in the shade. Leaving the app with the overlay up removes it without a response. A forced close
  has no response.
- `nudge_outcome` keeps "left the app within 10 minutes", and gains **whether the user reopened the app within the
  outcome window**. A forced close always "leaves the app", so for levels 2 and 3 the reopen is the only honest
  signal of what the friction changed.

### An escalation holdout, so the levels can be compared

Escalating deterministically after two snoozes would give every escalation a propensity of 1. The bandit could then
never tell whether an overlay does better than a notification in the same situation. When the rules ask for a
higher level, it is applied with a probability of 0.8. Otherwise the app stays at its current level for
that decision, and the probability is logged on every decision, as `holdout_probability` already is.

The existing holdout on whether to nudge at all stays unchanged (10%, then 50% since [0018](0018-tuning-for-data-collection.md)), and it still never applies to a snooze
follow-up.

### Where it lives

- **`core:nudge`**: the escalation rules and the ladder, as pure functions tested on the JVM, next to the
  existing rules. `NudgeConfig` gains the snoozes per level (2), the countdown (10 s), the escalation probability
  (0.8) and the pause length (1 hour). These are starting values; the bandit will tune them later.
- **`core:accessibility`**: an interface for what the service can do (show and dismiss an overlay, go home),
  implemented by `NudgiAccessibilityService`. The module keeps depending on no other module.
- **`feature:friction`**: the overlay UI, Compose inside the service's window, with the mascot from `core:mascot`.
  A `ComposeView` outside an activity needs its own lifecycle and saved-state owners. It stays out of
  `core:nudge`, which keeps pure logic testable on the JVM and has no UI dependency.
- **`feature:settings`**: the pause button, next to the data export.
- **`app`**: the wiring, as for the real-time trigger. `RealtimeNudgeTrigger` delivers each decision to the
  notifier or to the overlay, depending on its level.

The decisions of these rules are recorded with the policy id `rules_v2` (see [0017](0017-policy-id-on-nudge-decisions.md)).

## Consequences

- The accessibility service stops being passive. It still never reads screen content, but it now draws over other
  apps and can send the user home. `PRIVACY.md` and the onboarding explanation of that permission must say so.
- If the service is off, which is the case after a force-stop (see 0011), friction silently falls back to
  notifications. The logging makes that visible in the data instead of looking like a user who ignored overlays.
- The action space grows from "nudge or not" to "nudge or not × level". That needs more data per action; the
  escalation holdout is the price of being able to evaluate it.
- Testing: UI tools that drive the screen through `UiAutomation` unbind the accessibility service (see 0011). The
  overlay and the forced close have to be tested without them, or through JVM tests of the rules.
- The pause is an escape hatch the user can take every hour. If the data shows it used as a routine, the friction is
  too strong, and that is worth knowing rather than hiding.
- After a forced close the app is free until the next rule fires, which can be up to 15 minutes into a long
  session. The reopen recorded in the outcome shows how often that is used; a rule on reopening would be the next
  step if it is.
- Play Protect already flags the app. An app that draws over others and closes them will not look better; nothing
  to do about it for a sideloaded APK.
