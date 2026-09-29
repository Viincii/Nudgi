# 19. The reward the bandit learns from

## Context

The rule-based V1 records every nudge decision with its context, its action and a propensity (see
[0010](0010-rule-based-nudges.md), [0016](0016-progressive-friction.md), [0017](0017-policy-id-on-nudge-decisions.md)).
The next step is an on-device contextual bandit that chooses the action itself. Before choosing an algorithm, it
needs a reward: the one number that says, after the fact, how good a decision was. `CLAUDE.md` names reward
design as one of the two hard problems of the project, and whatever the bandit optimizes is what Nudgi will end
up doing.

The candidate so far is `nudge_outcome.left_app`: whether the user left the app within 10 minutes. The first real
export (five days, 40 shown or held-out nudges) shows its limits:

- **It rewards leaving, not stopping.** Of the 27 decisions followed by leaving the app, 10 were followed by 15
  minutes or more on watched apps within the next half hour: the user came back, or moved to another feed. Leaving
  Instagram for YouTube at midnight is a failure the binary outcome counts as a success.
- **It carries no cost.** With a reward that only measures benefit, the policy that maximizes it nudges every time,
  at the highest friction level, since doing nothing can never score better. The rules avoid that by hand, with a
  cooldown and a ladder; the bandit needs it in its objective.
- **It weighs every hour the same.** Scrolling at night costs sleep; scrolling at noon costs less. That is a value
  judgement of the user's, and nothing in the data expresses it.

## Decision

### The benefit: watched-app time avoided afterwards

For a decision taken at `t`, the benefit is the share of the following window **not** spent on watched apps:

```
benefit = 1 − (watched-app time in [t, t + window]) / window        ∈ [0, 1]
```

The window is **30 minutes during the day and 60 minutes at night** (below). At night the question is whether the
user went to sleep, which half an hour off the feeds does not answer.

- It counts **all** watched apps, not only the one nudged, so moving to another feed is not a success. Other
  phone use, messages included, does not count against it: the goal is fewer feeds, not no phone. Counting all
  screen time can be revisited once watched apps alone are understood.
- It is continuous: leaving for good scores close to 1, coming back after five minutes scores less, carrying on
  scores 0.
- It needs nothing new. Foreground and background events already give the watched-app time of any window.
- At night it measures what matters, being off the feeds, whether the user locked the phone or not.

On the first export, with a 30-minute window throughout, it averages 0.50 for shown nudges, against 0.34 for the three held-out ones; with three
controls that says nothing yet, but the measure behaves: spread over the whole `[0, 1]` range, and 0 exactly when
the user carried on.

### Night counts double

The benefit is multiplied by a weight that depends on the local time of the decision only: **2 from 00:00 to
06:00**, 1 otherwise. Night starts at midnight rather than at the 23:00 of the `late_night` rule: the user goes to
bed late, and the evening before midnight is not what they want to protect. The rule keeps its own window; the
reward only says how much an outcome is worth.

In a contextual bandit, a weight that depends on the context alone does not change which action is best in that
context. It matters through the cost below: at night the same effect of a nudge is worth twice as much, so it pays
for the nudge's cost more easily. Nudgi should then learn to intervene more readily at night, and to leave a small
effect alone during the day. The weight must never depend on the action, or the bandit would learn to prefer an
action for itself rather than for what it changes.

### Every action has a cost

```
reward = weight(t) × benefit − cost(action)
```

| Action | Proposed cost |
|---|---|
| Nothing (held out, or the bandit chose not to nudge) | 0 |
| Notification | 0.1 |
| Overlay | 0.2 |
| Countdown overlay | 0.3 |
| Forced close | 0.5 |

A cost reads as the minimum effect that justifies the action. A notification at 0.1 is worth showing during the
day if it saves at least 3 minutes of the next 30 on feeds, and at night if it saves 3 of the next 60, since the
benefit is doubled there. A forced close at 0.5 needs 15 minutes saved during the day and 15 at night. The costs
rise with the level because a stronger intervention is more annoying and wears out faster; the randomized
escalation of 0016 still shows whether the higher levels earn their cost. The costs stand for
annoyance and habituation, which the benefit cannot see; they are values to choose, not to learn.

### Derived, versioned, never stored

The reward is computed from `events` by a pure function when the bandit trains or is evaluated, not recorded as an
event. Each version of that function has an id (`reward_v1` for this one). Changing a weight or a cost then means
recomputing the rewards of the whole history, not losing it, and policies can be compared under the same reward.

The responses to a nudge (`stop`, `snooze`, `dismissed`) stay out of the reward. The first export confirms 0010's
reasoning: "I'll stop" was followed by leaving 10 times out of 11, but "5 more minutes" still 8 times out of 16.
They remain features and diagnostics.

## Consequences

- The reward of a decision is known 30 minutes after it, 60 at night (plus the merge gap), against 10 for the
  current outcome.
  Training and evaluation work on delayed feedback either way.
- Consecutive decisions overlap: a snooze follow-up 5 minutes after a nudge shares 25 of its 30 minutes. Their
  rewards are correlated, which the bandit's variance estimates will understate. Acceptable for a single user; to
  keep in mind when reading confidence intervals.
- `nudge_outcome` keeps being recorded as it is. It is cheap, it is what the home screen shows, and it keeps the
  data of V1 readable on its own.
- The night weight, the windows and the costs are the user's values, and are expected to move. They live in one
  place, versioned with the reward function, so each change is a new `reward_vN` recomputed over all the history.
- A benefit over 30 or 60 minutes still cannot see habituation over weeks. Daily watched time stays the metric to watch across
  policies, outside of the reward.
