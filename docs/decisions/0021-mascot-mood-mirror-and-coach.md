# 21. Nudgi's mood: a mirror at home, a coach in interventions

## Context

Nudgi's mood is a rule (see [0012](0012-home-mood-from-today.md)): worried from 2 hours on watched apps or 2 nudges
the user kept going after, neutral from 1 hour or 1 such nudge, happy otherwise. It shows on the home screen and on
the widget; the friction overlay always shows Nudgi worried; notifications show no face at all.

0012 already noted that the mood is itself an intervention: the user sees it and may react to it, and nothing
records it. The bandit (see [0020](0020-shadow-bandit.md)) can only ever learn the effect of an expression if the
data holds which one was shown, with some randomness in the choice, as it does for the holdout and the friction
escalation.

That raises a product question before a technical one. A mood that reflects the day honestly is legible and can be
trusted: worried means a heavy day. A mood chosen to make the user react is a lever that works best when it no
longer says anything about the day. Nudgi cannot be both in the same place.

## Decision

### A mirror at home, a coach in interventions

- **The home screen and the widget stay a mirror**, derived by the rule of 0012. It is a pure function of events
  already recorded, so what was shown at any moment can be recomputed rather than logged, as long as a change to
  the rule is logged here.
- **Every intervention is coached**: the notification and the two overlay levels. The forced close has no face and
  is not concerned. Nudgi's expression there is chosen per decision, to help the user stop, independently of the
  home mood: a heavy day at home can meet an encouraging Nudgi in the notification.

### Discrete expressions, drawn at random for now

- The coach picks among **discrete expressions drawn in `core:mascot`**, starting with the three that exist: happy
  (encouraging), neutral, worried. Each is a designed face, not a point picked in the space of face parameters.
- Until the bandit acts, **the expression of every shown intervention is drawn uniformly** and recorded with the
  decision, with its probability (`expression`, `expression_probability`). Held-out and suppressed decisions show
  nothing and record none. The draw changes the policy: decisions become `rules_v4`.
- Once the bandit acts, the expression becomes one dimension of its action, next to the friction level. It is then
  described by features of its own rather than multiplied into the list of actions, so what is learned about an
  expression carries over to every level. That is a new bandit version (`lin_ts_v2`).
- **Notifications show Nudgi.** The notification gets Nudgi's face as its large icon, drawn by `renderMascot`
  like the widget. The image is small, which is one more reason for marked expressions rather than fine shades.

### Growing the set of expressions

New expressions can be added over time, one at a time, each with a stated hypothesis ("a sleepy Nudgi works better
after midnight"), and a new policy id. Two things are needed before the set grows much:

- **Forgetting.** A new expression works partly because it is new. A bandit that weighs all its history equally
  keeps crediting it long after the user stopped noticing it. Older observations must weigh less, or be dropped
  past a horizon.
- **Shared features.** Each expression added splits a small volume of data further. Describing actions by their
  features, as above, is what keeps the learning of every expression from slowing down the others.

### Why not the seven face parameters

Letting the bandit set the seven parameters of `MascotFace` directly, to discover expressions or refine the
existing ones, was considered and rejected for now:

- The effect of an eye tilt on whether the user leaves a feed is tiny next to everything else that decides it. In
  seven continuous dimensions, finding it would take thousands of observations per region of the space; one user
  makes about a dozen a day. Refining an expression slightly is worse: the smaller the change, the more data it
  takes to see its effect.
- Exploring that space means showing arbitrary combinations, most of which look like a broken Nudgi rather than a
  new mood, and nothing guarantees the face it would converge on means anything to the user.
- The tool for it, Bayesian optimization over a continuous space, stays an option for **a single axis**: the
  intensity of an expression, blending two designed faces. Every point of that axis is a real face, and one
  dimension is learnable.

## Consequences

- For now, the expression of an intervention is random, so a worried moment can get a happy Nudgi. Interventions may
  lose a little of their effect meanwhile; that is the price of learning which expression works, as the holdout is
  the price of learning whether to nudge.
- The home mood and the intervention's expression can disagree. That is intended: one reports, the other acts.
- `core:nudge` records the expression by its id and never draws a face; the notifier and the overlay map the id to
  a `MascotMood`.
- Adding forgetting to the bandit becomes a prerequisite of growing the set of expressions, and a likely part of
  `lin_ts_v2`.
