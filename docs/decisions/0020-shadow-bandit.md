# 20. A shadow bandit next to the rules

## Context

The rules of [0010](0010-rule-based-nudges.md) and [0016](0016-progressive-friction.md) take every nudge decision
and record it with its context, its action and its propensity. The reward those decisions are judged by is settled
in [0019](0019-bandit-reward.md). The next step of the roadmap is an on-device contextual bandit that chooses the
action itself.

Letting it act now would be premature. There are about 13 decisions a day, a few hundred after a month, and none
yet at the friction levels above a notification. A bandit acting on that little data would mostly act at random,
and a bug in its features or its maths would reach the user as nudges before anyone noticed.

A shadow runs the whole bandit on real decisions, records what it would have done, and changes nothing. It tests the
features, the training and the choice on live data, and its recorded choices can be scored offline against the
rules with the holdout as control group. It goes live only once that comparison says it should.

## Decision

### Where it decides, and among what

The bandit decides at the same moments as the rules: whenever a rule fires and a decision is recorded, shown or
suppressed. The rules keep choosing **when** to consider a nudge; the bandit learns **what to do** at that moment.
Learning when to intervene from scratch would need far more data than one user produces.

Its actions are the ones the rules can take: nothing, a notification, an overlay, a countdown overlay, a forced
close. The forced close is not reserved to the rules' ladder: its cost of 0.5 already makes it hard to justify, and
leaving it out would decide in advance what the data should show. On a snooze follow-up the user asked to be
reminded, so "nothing" is not an option there, as the holdout already never applies to follow-ups.

### Linear Thompson sampling

For each action, a Bayesian linear regression predicts the weighted benefit of 0019 from the context. To decide,
the bandit draws one set of weights per action from its posterior, predicts each action's weighted benefit,
subtracts the action's cost (a known value, not learned), and takes the best.

- **Thompson sampling rather than UCB**: it is randomized, so every choice has a propensity. Once the bandit acts,
  its own decisions can then be evaluated offline and a later policy compared with it, which a deterministic UCB
  choice makes impossible. The propensity of the chosen action is estimated by drawing again (proposed: 200 draws).
- **Linear and small**: about 15 features and 5 actions. The posterior of each action is a 15 × 15 matrix; no
  library is needed, and everything runs on the JVM for the tests.
- **Prior**: every action starts at a benefit of 0.5, the middle of its range, with a wide variance. Actions never
  tried keep wide posteriors and get explored; in a shadow, exploring costs nothing.
- **Stateless**: the model is refit from the whole history at every decision rather than stored and updated. At a
  few hundred decisions this takes a few milliseconds, it cannot drift from the data, and changing the
  features or the reward recomputes it at once. A stored model can come when the history makes refitting slow.

### Where training happens

On the phone, continuously. A linear bandit's training is two running sums per action, a matrix and a vector; the
model is one solve of a 15 × 15 system. That is milliseconds, not a job for a bigger machine. And a bandit has to
learn from each outcome as it comes: a model trained on a laptop and shipped in the app would keep exploring without
learning anything from it until the next export, retrain and install.

The laptop is where the bandit is judged, not fitted. An analysis script in `tools/` reads an export, replays the
history, estimates what the shadow would have achieved against the rules, and is where features, prior and reward
are chosen. The phone learns; the laptop evaluates.

### Features

From the context snapshot already recorded on every decision, plus the friction state:

- time: hour of day as sine and cosine, weekend, night as defined by the reward (00:00 to 06:00);
- usage: session, today and late-night minutes, on a log scale so the first minutes weigh more than the hundredth;
- nudging: nudges shown today, minutes since the last one (capped), snoozes today on this app, the friction level
  reached;
- the rule that fired, one-hot;
- a constant.

The app itself is not a feature yet: nearly every decision so far is on Instagram, so it would carry no
information.

### Training data

Every recorded decision whose reward window has closed, with the action actually taken: the friction level applied
for a shown nudge, "nothing" for a held-out one. Suppressions by the cooldown are "nothing" too, deduplicated to
the first per rule, level and session (see 0010). Decisions of every policy id are used; the policy that took them
does not matter to a model of what each action yields in a context, as long as that context is in the features.

### What is recorded

`nudge_shown` and `nudge_suppressed` gain a `shadow` object: the bandit's policy id (`lin_ts_v1`, covering the
features, the algorithm and `reward_v1`), the action it would have taken, its propensity, and how many decisions it
was trained on. Nothing else changes, and the rules' decision is taken exactly as before.

### Where it lives

A new `core:bandit` module, pure Kotlin with no Android types: the features, the reward function of 0019, the
regression and the sampling. It depends on nothing; `core:nudge` builds its training set from `events` and calls it
from `NudgeEvaluator`. A failure in the bandit is caught and recorded as a missing shadow, never allowed to stop a
nudge.

### Toward reinforcement learning

A contextual bandit is reinforcement learning with one simplification: it assumes an action does not change the
situations that follow. Nudgi's actions do. A nudge at 20 minutes changes what happens at 40, too many nudges today
make the next ones weaker, and the friction ladder is a sequential choice by nature: escalating now or waiting
plays out over the whole evening. The bandit only sees that history through its features (nudges today, snoozes,
level reached), and never plans.

It is still the right first step. Full reinforcement learning needs far more experience than one user's dozen
decisions a day; learning it directly on the user would take years and be unpleasant meanwhile. Everything the
bandit requires carries over unchanged: the reward, exploration, delayed feedback, logged propensities and offline
evaluation. The data it produces, every decision with its context, action, propensity and timestamp on top of the
full usage timeline, can be read back as trajectories, which is what a sequential learner trains on.

The path this leaves open, for roadmap step 8:

1. **A simulator of the user**, learned from the bandit's months of data: how likely the user is to leave, come
   back or snooze after each action in each context.
2. **Reinforcement learning against the simulator, on the laptop**, where thousands of simulated evenings cost
   nothing and a heavier model, such as a small neural network trained with PPO, makes sense. Its policy is then
   shipped in the app.
3. **Off-policy evaluation on the real logs before deploying it**, since policies trained in simulation often fail
   on the real thing. These are the same tools as for the shadow.
4. **The friction ladder as the first sequential problem**: a small state (level, snoozes, hour, session length),
   visible consequences, and a decision that is sequential by nature.

## Consequences

- The export gains the shadow's choices. The script in `tools/` scores them with the holdout: inverse propensity
  weighting over the decisions where the rules happened to take the action the shadow chose. A debug screen in the
  app can come later if looking at the numbers on the laptop proves too slow.
- With no data yet at the higher friction levels, their posteriors stay wide and the shadow will often pick them.
  That is the exploration Thompson sampling is meant to do, and costs nothing while it is only a shadow.
- Going live is a separate decision, taken on the shadow's offline results, with the policy id of the decisions
  switching from `rules_v3` to the bandit's.
