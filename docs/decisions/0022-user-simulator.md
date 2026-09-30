# 22. A simulated user to test the bandit against

## Context

The shadow bandit of [0020](0020-shadow-bandit.md) (`lin_ts_v1`) records what it would have done on every real
decision. Real decisions come at about 13 a day, so whether it learns anything useful will only show after weeks,
and three questions cannot be answered from real data at all, because the truth is unknown there:

1. **Is the bandit correct?** Given a user whose reactions are known, does `LinearThompsonSampling` end up choosing
   the best action in each context? A sign error in a feature or a wrong prior would not crash anything; it would
   just learn badly and quietly.
2. **How many decisions does it need?** Before going live, the question is not "is it better than the rules today"
   but "after how many decisions is it likely to be". That turns into a number of days at 13 a day, and says whether
   waiting a few weeks is realistic.
3. **Can `tools/bandit_report.py` be trusted?** Its inverse propensity weighting estimate of the shadow's reward is
   unbiased in theory, but with few decisions where the rules happened to take the shadow's action its variance can
   make it useless. Only a world where the true value of the shadow's policy is known can say how wrong the estimate
   is at 100, 300 or 1000 decisions.

A simulated user answers all three: a small program that plays the user, whose reactions follow rules written down
in advance. The bandit runs against it, unchanged, and its choices can be compared with the best ones, which are known
because the simulator's rules are.

This is not the simulator of step 1 of the path in 0020. That one is *learned* from months of real data, to train a
reinforcement learning policy on. This one is *written by hand*, to test the bandit and the evaluation. It can be
calibrated on the export as far as the data goes, which makes it a first draft of the other one, but its purpose is
testing, and it is judged on whether its scenarios are useful, not on whether they are faithful.

## Decision

### What the simulator simulates: decisions, not days

Two levels are possible:

- **Decision level.** The simulator produces the contexts in which a rule fires, and for a context and an action,
  returns a benefit. Everything in between (the phone, the sessions, the rules deciding when to fire) is skipped.
- **Event level.** The simulator plays a whole day minute by minute: opening apps, scrolling, the rules firing,
  nudges, responses, leaving, coming back. It writes `events`, and the real pipeline computes everything from them.

**Decision level first.** It answers the three questions above, it is a few hundred lines, and one run of
thousands of decisions takes seconds. Its limit is that an action changes nothing that follows: a nudge at 20
minutes does not change the context at 40. That is exactly the assumption the bandit makes (see "Toward
reinforcement learning" in 0020), so it is fair for testing the bandit, and misleading for anything sequential. The
event level is what reinforcement learning on the friction ladder will need, and belongs to its own decision.

### Where contexts come from: the real ones

A context is drawn by resampling a real decision from an export: its hour, weekday, session and daily minutes,
nudges and snoozes so far, friction level reached and rule. The distribution of contexts is then the user's own
(mostly Instagram, mostly evenings, a peak after midnight), rather than an invented one, and features that are
nearly constant in real life stay nearly constant in the simulation. A synthetic generator is kept for the unit
tests, which must not depend on an export.

### How the simulated user reacts: stop or carry on

The real benefit is not a smooth quantity around a mean. On the first export it is bimodal: close to 1 when the user
put the phone down, close to 0 when they carried on, with some in between when they came back. A simulated user that
returns `mean + Gaussian noise` would be kinder to the bandit than the real one, whose regression assumes exactly
that noise.

The simulated user decides, for a context and an action, **whether they stop**, with a probability
`p(context, action)`. If they stop, they may still come back after a delay drawn from a distribution; if they carry
on, they may still leave on their own after a while. The benefit follows from that time, through the same
`RewardV1` formula. This has three advantages:

- the benefit has the shape of the real one, and the mismatch with the bandit's Gaussian noise is tested rather than
  hidden;
- the parameters read as sentences ("a notification after midnight makes me stop 30% of the time"), so a scenario
  can be written and discussed by the user it imitates;
- the probability that the user stops without any nudge is directly the effect of "nothing", which the holdout
  already measures.

### Scenarios: worlds with a known answer

Each scenario is one simulated user, with the best action in every context known:

| Scenario | What it tests |
|---|---|
| **Linear** — the stop probability is linear in the bandit's own features | Correctness. The model is right by construction; if the bandit does not converge here, it is a bug. |
| **Calibrated** — the effect of "nothing" and of a notification fitted on the export; higher levels set by hand | How many decisions the real situation may need. The main scenario. |
| **Nothing works** — every action is as good as nothing | That the costs of 0019 stop the bandit from nudging for nothing, and how long it explores before it does. |
| **Interaction** — the forced close only works on long sessions after midnight | Misspecification: an effect the linear model cannot represent exactly. |
| **Habituation** — each action's effect decays with the number of times it was shown in the last weeks | Non-stationarity: how badly a bandit that never forgets does, which is what `lin_ts_v2`'s forgetting is for (see [0021](0021-mascot-mood-mirror-and-coach.md)). |

The calibrated scenario can only be calibrated where data exists: the notification and "nothing" have dozens of
decisions, the overlays a handful, the countdown overlay and the forced close none. Where there is no data, the
scenario says what it assumes, and is run at both extremes: the untested levels clearly more effective than the
overlay, and no more effective than it. If the conclusion changes between them, it depends on the assumption, not on
the data.

The first version has the linear, calibrated and nothing-works scenarios. Interaction and habituation come with
`lin_ts_v2`, whose forgetting they are meant to justify.

### What is measured

Every run is repeated over many random seeds, and reports distributions, not single numbers:

- **Regret**: at each decision, the expected reward of the best action minus that of the bandit's choice. Its
  cumulative curve flattening is what learning looks like; a straight line is a bandit that learns nothing.
- **Against the rules**: the expected reward of the bandit's choices against that of the rules' (holdout 0.5,
  escalation 0.8, as in `NudgeConfig`), as a function of the number of decisions seen. The point where the bandit is
  ahead in 80% of the seeds, divided by 13, is the number of days to wait before going live. This is the number the
  whole simulator exists to estimate. 80% rather than 95%: the costs of 0019 make a bandit that is worse than the
  rules mostly an annoying one, not a harmful one, and waiting for near certainty would cost weeks of it learning
  nothing live.
- **The report script**: the simulator writes a synthetic export in the real format, with the rules' logged decisions
  and the shadow's recorded choices, and `bandit_report.py` runs on it unchanged. Its IPS estimate is then compared
  with the shadow's true value, which the simulator knows. The error of the estimate at a given number of decisions
  says how many real ones are needed before the report means anything.

Writing a synthetic export at the decision level only needs, for each decision, a `nudge_shown` or
`nudge_suppressed` event with its context, and watched-app usage after it that gives the benefit the simulated user
chose. The script cannot tell it from a real export, which is the point.

### Where it lives: a JVM module running the real code

The bandit must be tested as it runs on the phone, so the simulator is written in Kotlin and calls
`LinearThompsonSampling` itself, rather than reimplementing it in Python. `core:bandit` is an Android library
today although it holds no Android type, and a JVM module cannot depend on an Android library. So:

- `core:bandit` becomes a plain Kotlin JVM library, with a new `nudgi.jvm.library` convention plugin. It stays one
  module with one copy of the code: Android modules can depend on a JVM library, so `core:nudge` uses it as before,
  and the simulator uses the same classes. That 0020's "no Android types" is then enforced by the compiler rather
  than by convention is a side benefit.
- A new `tools:simulator` module, a JVM application: `./gradlew :tools:simulator:run --args="calibrated
  path/to/export.zip"` prints the summary and writes the synthetic export and the curves as CSV. It is not part of
  the app and never ships.
- One fast, seeded unit test in `core:bandit` runs the linear scenario for a few hundred decisions and checks the
  bandit converges, so a regression in the maths fails CI. The long runs stay out of CI.

The curves are plotted by a Python script next to `bandit_report.py`, if plotting proves useful at all.

## Consequences

- The bandit's hyperparameters (prior precision, noise standard deviation, number of propensity draws) get a place
  to be chosen, rather than the values proposed in 0020 by reasoning alone. `noiseSd = 0.3` in particular was
  chosen before the benefit was known to be bimodal.
- The number of days before going live becomes an estimate with a spread, conditional on the scenarios. It will be
  optimistic if the real user is harder than every scenario, which is why the unfavourable ones are there.
- A bandit tuned until it beats the simulator is tuned to the simulator. The simulator decides whether the bandit
  works and how long it needs; whether it goes live is still decided on the real shadow's results, as 0020 says.
- Rewards of real decisions are correlated when their windows overlap (see 0019), and simulated ones are not: the
  simulator will make the report's estimates look more precise than they are on real data.
- The scenario files are the first written model of how the user reacts to Nudgi. When reinforcement learning comes,
  the learned simulator starts from them.
