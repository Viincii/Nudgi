# 10. Rule-based nudges

## Context

Usage events are polled into `events` and aggregated into `daily_stats` (see [0008](0008-usage-stats-polling.md)
and [0009](0009-daily-stats-aggregation.md)). The next step is the rule-based V1: actually nudging the user.
`CLAUDE.md` frames V1 as both a real product and the data-collection phase for an on-device contextual bandit, so
the rules matter less than what gets recorded around them. For each decision the bandit will need the context, the
action taken (including "do nothing") and an outcome to turn into a reward.

## Decision

### Rules

Only watched apps are nudged: those whose `ApplicationInfo.category` is social, video or news, plus a short list of
known feed apps that declare a wrong category or none. Nudgi itself is never watched. A `<queries>` element for
launcher activities gives the package visibility this needs, without `QUERY_ALL_PACKAGES`.

A rule only fires for the watched app currently in the foreground. In priority order:

| Rule | Fires when | Repeats |
|---|---|---|
| `late_night` | 10 minutes on watched apps (combined) between 23:00 and 06:00 | Once per night |
| `snooze_followup` | 5 minutes after the user tapped "5 more minutes", still in the same session | After each snooze |
| `long_session` | 20 minutes in one session | Every 15 minutes after that, one level up each time |
| `daily_budget` | 60, 90 then 120 minutes on the app today | Once per threshold |

A session merges foreground pieces of the same app less than a minute apart, because `ACTIVITY_PAUSED` and
`ACTIVITY_RESUMED` fire on every activity switch within an app. Guardrails on top: at most 8 nudges shown a day,
and 10 minutes between two nudges shown. A snooze follow-up skips the cooldown, since the user asked for it.

All thresholds live in `NudgeConfig`. The rules, the context built from `events`, the outcomes and the metadata
are pure functions tested on the JVM; only the notification, the watched-app lookup and the database stay behind
interfaces.

### Holdout

10% of the nudges that would otherwise be shown are randomly withheld and recorded as suppressed with the reason
`holdout`. The draw happens after the guardrails, so the held-out nudges are comparable to the shown ones. They give a
control group, the only way to measure what the nudges change, and let the bandit's policies be evaluated offline
later. The holdout probability is logged on every decision, so each action's propensity can be recovered. A snooze
follow-up is never held out.

### Events

Every decision gets a `nudge_id`, and every nudge event carries it in its JSON `metadata`
(kotlinx.serialization, snake_case keys):

- `nudge_shown` and `nudge_suppressed`: the rule, its level and threshold, the `reason` for a suppression
  (`cooldown`, `daily_cap`, `holdout`, `notifications_disabled`), the holdout probability, and a context snapshot:
  session length, daily and late-night usage, local hour, weekday, nudges shown today, time since the last one.
  Suppressions record the "do nothing" action; without them the bandit would only ever see the cases where
  something was done.
- `nudge_response`: `stop`, `snooze`, or `dismissed` when the notification is swiped away.
- `nudge_outcome`: whether the user left the app within 10 minutes, and after how long, for shown and held-out
  nudges alike. This is the candidate reward. It measures what the nudge changed, where the buttons mostly measure
  politeness.

`daily_stats.nudge_count` now counts `nudge_shown` events.

### Pipeline

The periodic worker now runs polling, aggregation, then the rules, against one instant. It moved to `app`,
the only module that depends on all three. Runs are serialized by a mutex, because two overlapping polls would
insert the same window twice. Its unique work name changed, and the old one is cancelled on start: its worker class
is gone, and `KEEP` would leave it failing forever.

Onboarding gains a third step for notifications: nudges are notifications, and without them Nudgi can only watch.

## Consequences

- With polling alone, a nudge fires up to 15 minutes after its threshold. Triggering from the accessibility
  service, which already sees foreground changes, is the next step and reuses this engine unchanged.
- On the very first poll after install, a session already running when the poll window opens is not seen: its
  foreground event is before the window and is not inserted.
- "I'll stop" only records the answer. It does not send the user home, so leaving the app stays a genuine outcome
  rather than a side effect of the button.
- Cooldown and cap suppressions are recorded again at every evaluation while they last. That is bounded by the
  polling rate, but the bandit's data preparation has to deduplicate them per rule and level.
- The metadata keys are the contract the model will be trained on: renaming one needs a migration of the rows
  already recorded.
