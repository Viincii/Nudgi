# 25. The bandit goes live

## Context

[0023](0023-rules-explore-friction-levels.md) set two conditions for the shadow `lin_ts_v2` to start deciding. The
export of 2026-11-12 (33,916 events from 2026-09-24, 393 decisions with a closed reward window, 219 of them under
`rules_v5`) meets both:

1. **The simulator calibrated on it puts the shadow ahead of the ladder in 80% of the seeds** from 50 decisions on
   in both calibrated worlds (6 days at 8.1 decisions a day; 100% of seeds from 100 to 200 decisions on). The
   uncalibrated linear world gets there at 300 decisions, and the world where nothing works at 50. Live, the bandit's
   regret falls to 0.015 to 0.02 per decision against 0.06 to 0.11 for the ladder.
2. **The report does not show it clearly worse**: shadow − ladder is +0.06 ± 0.10 by IPS, +0.06 ± 0.08
   self-normalized, over the 219 `rules_v5` decisions.

The calibration still fits only nothing and the notification on real data; the higher levels are assumed at both
extremes. Both extremes pass, so this does not block, but the worlds will move as data on the overlays comes in.

The same export answers a question 0023 left aside: whether the rules work at all. The holdout is the only
randomized comparison in the data:

| Decisions | Nothing (holdout) | Shown | Difference, 95% interval |
|---|---|---|---|
| All policies | 0.46 (95) | 0.52 (222) | +0.056 [−0.02, +0.14] |
| `rules_v5` | 0.48 (36) | 0.52 (141) | +0.040 [−0.09, +0.17] |
| `daily_budget` | 0.45 (43) | 0.55 (88) | +0.106 [−0.01, +0.23] |
| `long_session` | 0.42 (41) | 0.41 (101) | −0.005 [−0.13, +0.12] |

The benefit is that of [0019](0019-bandit-reward.md). +0.056 is under 2 minutes of feeds avoided in 30, below the
3 minutes that justify a notification's cost of 0.1. On the reward, always doing nothing scores 0.63 by
self-normalized IPS over the `rules_v5` decisions, the ladder alone 0.52. Weekly feed time shows no trend since
September (160 to 260 minutes a day).

Under `rules_v5`, when the ladder asks for a notification, the draws give randomized arms in the same contexts:

| | Nothing | Notification | Overlay |
|---|---|---|---|
| Day | 0.40 (29) | 0.41 (65) | 0.49 (29) |
| Night | 0.84 (6) | 0.82 (13) | 0.73 (7) |

By day, a notification does exactly what nothing does (+0.007, [−0.15, +0.15]); an overlay does a little better
(+0.08 over the notification, not significant). The shadow has learned the same thing: by day it chooses nothing on
108 of 181 decisions, and an overlay rather than a notification when it acts.

The user reports the same from the other side: by day the nudges annoy without stopping them, and at night Nudgi is
perhaps not strict enough. The day half is what the data shows. The night half is not shown either way: 38
`rules_v5` decisions at night, the ladder asks for a notification on 29 of them, the benefit is already 0.84 with
nothing, and the 7 overlays did no better. Calibrated on all policies, the notification does raise the
probability of stopping at night, from 0.58 to 0.66, which the `rules_v5` data alone is too small to confirm or
contradict.

## Decision

### `lin_ts_v2` decides, with a floor of rules

The bandit goes live now, without waiting for 300 `rules_v5` decisions: both conditions of 0023 are met, and the
linear world, the only one still short of 80% of seeds, gets there at 300 decisions, about as long as implementing
this takes.

The bandit takes the decisions the rules took: same moments, same support (nothing except on a follow-up, the
ladder's level and its neighbours, the forced close), same features, prior and reward. Its decisions carry the policy
id `lin_ts_v2`.

One decision in ten is still drawn from the rules, which keep their holdout, exploration and forced close. The
logged `action_probabilities` are those of the mixture, `0.9 × bandit + 0.1 × rules`, with the bandit's
probabilities estimated by drawing again as today. Two reasons:

- **The ladder stays measurable.** Going live is a bet the simulator calls good and the report cannot confirm
  (0023). Comparing the live bandit with the ladder afterwards needs the ladder's action to keep a chance in every
  context; with a prior of 1024 the bandit can leave some actions near zero probability, and IPS then divides by
  almost nothing.
- **Nothing keeps its measurements.** The bandit is restricted to the rules' support, so it keeps learning from the
  actions the rules take, including the ones it would no longer choose.

The ladder keeps counting its own level, so a level the bandit chooses neither raises it nor restarts it, as for the
explored levels of 0023. Snoozes keep raising it.

### At night the ladder starts at the overlay

From 00:00 to 06:00, the night of the reward, the ladder asks for an overlay where it would ask for a notification.
Its neighbours are then the notification and the countdown overlay, so the support at night keeps everything it has
today and gains the countdown overlay, where there is no night data at all. The rules become `rules_v6`.

This is exploration at the user's request, not a finding: nothing in the data says a stricter night does better.
It costs little, since the bandit can still choose a notification or nothing at night, and it is the only way the
night data will ever say whether the stronger levels pay.

### The reward stays `reward_v1`

By day the bandit already does what the user asks: it mostly chooses nothing, because a notification does nothing
there. Raising the day costs would push it further the same way, and would invalidate the simulator runs above,
which were made under `reward_v1`. The day cost is reconsidered after a few weeks live, if the nudges still annoy.

### A way back

- **A switch in Settings**, next to the friction pause and in every build, returns the decisions to the rules
  (`rules_v6`, holdout and exploration included) until switched back. It is the user's way out, like the pause, so
  it is not gated on `BuildConfig.DEBUG`. It is recorded as an event, like the pause.
- **Rollback criterion**: after 300 live decisions, the report estimates the live bandit and the ladder over the
  live decisions. If the bandit is below the ladder by more than the spread of the difference, the decisions go back
  to the rules and a new ADR looks at why.

### The decision has to stay fast

The shadow loads every event since the beginning (`eventDao.since(0L)`) at each decision, before the nudge is shown.
Live, that delay sits between the rule firing and the user seeing the nudge. It is measured on the phone before going
live; above about 100 ms, the per-action sums of the regression are cached and updated with each closed decision
instead of replaying the whole history.

### What the report shows

`tools/bandit_report.py` separates the live decisions from those of the rules floor, estimates the live bandit and
the ladder alone over all decisions with logged probabilities, and keeps showing the holdout comparison above by day
and by night.

## Consequences

- The user meets fewer nudges by day, mostly overlays when there is one, and more overlays at night.
- The holdout falls from one decision in five to one in fifty (`0.1 × 0.2`) as an explicit draw, plus whatever the
  bandit chooses. Measurements of nothing now come mostly from the bandit's own choices, which are not randomized
  the same way; their logged probabilities keep them usable.
- The simulator models `rules_v5`, not the mixture or the night ladder of `rules_v6`; it has to learn both before
  its next calibration means anything.
- The bandit still does not forget. A preference that changes over weeks, or habituation to the overlays, shows in
  its decisions only slowly; forgetting stays planned for `lin_ts_v3` (0021).
- The benefit window still cannot see effects beyond 30 or 60 minutes. Daily feed time stays the metric to watch
  across the switch.

## Alternatives considered

- **Waiting for 300 `rules_v5` decisions** before going live. It would only add the linear world, which is not
  calibrated, to conditions already met.
- **A larger floor, or none.** More floor measures the ladder better and imposes more of its cost. None relies on
  Thompson sampling's own randomness, where an action the bandit almost never picks gets IPS weights that explode.
- **A heavier night weight** (3 instead of 2), which lowers every cost at night by a third so the bandit intervenes
  more readily there. It changes the reward and every past value with it, for a preference the starting level of
  the ladder expresses without touching the reward.
- **Leaving the night to the bandit alone.** Its data at the higher levels at night would stay close to nothing.
- **Raising the day costs now** (`reward_v2`). The annoyance the user reports is what the costs stand for, but the
  bandit already holds back by day, and the simulator runs behind this decision were made under `reward_v1`.
- **The way back in debug builds only.** It is the user's way out, not a development tool.
