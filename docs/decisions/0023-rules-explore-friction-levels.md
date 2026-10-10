# 23. The rules explore the friction levels

## Context

The simulator of [0022](0022-user-simulator.md), run on the first real export (110 decisions with a closed reward
window over 15 days), found that the shadow bandit of [0020](0020-shadow-bandit.md) cannot be judged, and cannot
learn, from the data the rules collect:

- **The rules cover a fifth of what the shadow chooses.** For a given decision the rules can take two actions:
  nothing (the holdout) or the friction level reached, occasionally the one above on an escalation. The shadow
  chooses among all five. Over the real contexts, only 20% of its choices are actions the rules could have taken.
  On the real decisions so far, 43 of its 67 choices were a countdown overlay or a forced close, which the rules
  never applied.
- **Off-policy evaluation needs that support.** The inverse propensity weighting of `tools/bandit_report.py` counts
  a choice the rules could never have made as a reward of 0: its estimate of the shadow is off by −0.2 to −0.4, and
  more decisions do not fix it. The self-normalized estimate only measures the fifth the rules cover, and is off by
  up to +0.25. No estimator can do better: what an action yields where it was never taken is not in the data.
- **Training needs it too.** Actions the rules never take keep the bandit's prior, so the shadow keeps choosing
  them. In the simulated worlds calibrated on the export, it never gets ahead of the rules.
- **The prior makes it worse.** Every weight starts with a standard deviation of 0.5, and the features include
  logarithms of minutes that reach 4 or 5. Before any data, the prior allows predicted benefits of several units on a
  quantity between 0 and 1, so an action with little data looks promising for a very long time.
- **Decisions are rarer than assumed**: 7.6 a day, not 13.

## Decision

### Exploration around the level the ladder applies

The rules keep deciding when a nudge is considered, and the ladder of [0016](0016-progressive-friction.md) keeps
deciding the level it asks for, with its escalation applied 80% of the time. On top of that, `rules_v5` draws:

- **The holdout falls from 50% to 20%.** The holdout is exploration of "nothing"; at 20% a held-out decision weighs 5
  in the estimate, still reasonable. The room it frees goes to the levels, where data is missing.
- **A shown nudge moves to a neighbouring level 30% of the time**, split between the level below and the level above
  when both exist. Both directions, so the bandit can learn when a lighter intervention is enough: at the countdown
  overlay, whether a notification would have done, saving 0.2 of cost each time.
- **A shown nudge becomes a forced close 5% of the time**, whatever the level reached. The bandit may choose the
  forced close anywhere (0020); it can only be judged on it if the rules sometimes do the same. That is about two
  forced closes a week. The pause in Settings remains the way out when it gets too unpleasant.

A shown nudge then stays at the ladder's level 65% of the time. Snooze follow-ups keep no holdout, as before, and are
explored the same way. A pause caps the applied level to a notification as before, and no exploration applies during
it.

### The bandit: restricted to the rules' actions, with a tight prior

Two changes make a new shadow policy, `lin_ts_v2`. The version [0021](0021-mascot-mood-mirror-and-coach.md) planned
with forgetting and action features becomes `lin_ts_v3`.

- **It only chooses among the actions the rules could have taken** for the decision: nothing (except on a
  follow-up), the levels the ladder and its neighbours give, and the forced close. Every choice then has a propensity
  under the rules, so IPS is unbiased again, and the bandit only explores where data comes in.
- **Its prior precision goes from 4 to 1024**: a standard deviation of 0.03 per weight instead of 0.5. A prior is
  worth precision × noise² observations, here 1024 × 0.3² ≈ 92 observations per action, against 0.4 before. Centred on
  the same benefit for every action, it says "every action yields the same until the data shows otherwise", so the
  costs decide and the bandit defaults to the cheaper action. It moves to a costlier one once about a hundred
  observations of it say it pays.

The prior mean (a benefit of 0.5) and the noise (standard deviation 0.3) stay: the real benefits average 0.48 with a
standard deviation of 0.33.

### Logging every action's probability

Every decision records the probability of each action under the rules, as an `action_probabilities` object keyed by
action id, computed after the holdout, the escalation, the exploration and any pause. It replaces recomputing the
propensity from `holdout_probability`, `escalation_probability` and the levels, which the report does today and which
would grow with every new kind of draw. An `explored` field (`neighbour`, `forced_close`, or absent) says what the draw
did, for reading the data. A decision whose friction fell back to a notification because the accessibility service
was not running did not take the action drawn, and stays out of the evaluation.

### What the bandit has to beat: the ladder alone

The rules collecting the data are not the right comparison: their holdout and their exploration have a cost, and
make them easy to beat. The bandit is compared with **the ladder alone**, what Nudgi would do with no bandit and no
measurement: always the level the ladder asks for, with no holdout and the escalation always applied. Under
`rules_v5` the ladder's action always has a chance, so its value can be estimated from the logs like the shadow's.

### What the report shows

`tools/bandit_report.py` reads `action_probabilities` when present and recomputes the propensities as today
otherwise. It prints the share of decisions where the rules could have taken the shadow's choice, estimates the shadow
and the ladder by IPS and self-normalized IPS over the decisions where that share is complete, and gives the spread
of each estimate and of their difference.

### When the bandit goes live

When both hold:

1. **In the simulator calibrated on the latest export, the shadow is ahead of the ladder in 80% of the seeds**, in
   every calibrated world. As `rules_v5` collects data on the overlays and the forced close, the calibration fits
   them too, instead of assuming them at both extremes.
2. **The report does not show the shadow clearly worse than the ladder**: their difference is not below zero by more
   than its spread.

The second condition cannot confirm a gain, only rule out a clear loss (below); the first is what says the bandit
is worth it.

### The figures behind these choices

From the simulator on the real contexts, 30 seeds per configuration, at 7.6 decisions a day:

| Rules | Prior | Shadow ahead of the ladder in 80% of seeds: low / high / linear world | IPS bias at 300 decisions |
|---|---|---|---|
| `rules_v4` | 4 | never / never / never | −0.24 / −0.43 |
| `rules_v5` | 4 | 261 days / 6 days / never | about 0 |
| `rules_v5` | 64 | 104 days / 6 days / 392 days | about 0 |
| `rules_v5` | 1024 | 6 days / 6 days / 104 days | about 0 |

The low world assumes the levels above a notification do no better than it, the high world that each does clearly
better, the linear world that the truth is linear in the bandit's own features. 6 days is the first point measured
(50 decisions). Once live, the bandit with a prior of 1024 loses a third to two fifths of what the ladder loses against
the best action (a regret of 0.020 to 0.024 per decision, against 0.053 to 0.065).

- **Tighter priors kept doing better** in every world tried, up to 4096. 1024 is a choice, not the optimum: the
  forced close collects about 0.3 observations a day, so its data only weighs as much as the prior after about 300
  days, and four times longer at 4096. A prior too strong finds a gain late; a prior too weak imposes costly
  interventions at random. For an app the user lives with, the first is the error to prefer.
- **With the tight prior, the rates barely change when the shadow gets ahead.** They matter for the long term,
  feeding the rarer levels the prior needs data on, and for the spread of the report: 0.09 to 0.10 at 300 decisions
  with these rates. With the old prior, 2% of forced closes instead of 5% kept the shadow from ever getting ahead
  in one world and took more than twice as long in the other, for lack of data on it.

## Consequences

- **The report can rule out a much worse bandit, not confirm a slightly better one.** Its spread at 300 decisions is
  about 0.1, and the gains the simulator finds over the ladder are a few hundredths. Confirming such a gain from real
  data alone would take tens of thousands of decisions; the simulator carries that part of the decision, which makes
  its calibration on each new export part of going live.
- **The user meets interventions chosen at random**: about one shown nudge in three at another level than the
  ladder's, about two forced closes a week. That is the price of learning on one person; the pause is the way out.
- **Fewer nudges are held out**, so fewer measurements of doing nothing: one decision in five instead of one in two.
- Decisions recorded under `rules_v1` to `rules_v4` keep their value for "nothing" and the notification, and stay
  outside the support for the rest. The sooner `rules_v5` ships, the less of that.
- The early lead of the shadow comes mostly from doing less than the ladder, which the costs reward. The calibrated
  worlds rest on few decisions, the night on six held-out ones, and will move as data comes in.
- `rules_v5` and `lin_ts_v2` ship together: the restriction is what makes the exploration useful to the bandit, and
  the exploration is what makes the restriction leave the bandit a choice.
