# 17. A policy id on every nudge decision

## Context

Nudge decisions are recorded with their context, their action and a propensity, so an on-device contextual bandit
can later learn from them (see [0010](0010-rule-based-nudges.md)). Until now every decision was taken by the same
rules, so nothing recorded which policy took it.

That stops being true soon. The progressive friction (see [0016](0016-progressive-friction.md)) changes the rules,
and a bandit, first in shadow mode then live, will take decisions of its own. Offline evaluation of a new policy
reweights the logged decisions by the propensity of the policy that took them: mixing the data of two policies
without telling them apart makes that estimate wrong, and the rows cannot be relabelled afterwards.

## Decision

`nudge_shown` and `nudge_suppressed` gain a `policy_id`. The rules as they stand are `rules_v1`, a constant next to
the metadata (`RULES_POLICY_ID`). It is bumped whenever the way decisions are taken changes: new rules, new actions,
new thresholds or probabilities in `NudgeConfig`.

Rows recorded before the field existed have no `policy_id`; they were all taken by `rules_v1`. The field is nullable
so they still decode, rather than migrating the rows already on the device.

## Consequences

- The bandit's data preparation reads a missing `policy_id` as `rules_v1`.
- Changing a default in `NudgeConfig` without bumping the id silently mixes two policies. The id sits next to the
  metadata contract, which already warns that renaming a key needs care.
