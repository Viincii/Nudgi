# 18. A 50% holdout, no daily cap, no messaging apps

## Context

After five days of real use on the dev device (2,789 events, 65 nudge decisions), the first export showed three
problems with the data the rules of [0010](0010-rule-based-nudges.md) produce:

- **Almost no control group.** The 10% holdout withheld 3 nudges out of 40 that would have been shown. At that rate
  it takes months before the effect of a nudge can be measured, or a policy evaluated offline.
- **The daily cap starved the evening.** Eight nudges were shown by 14:47 on a heavy day, mostly snooze follow-ups.
  The next four hours of use, late night included, got no nudge at all, and 22 `daily_cap` suppressions were
  recorded instead, 15 of them for the same late-night nudge within twenty minutes: the real-time trigger
  re-evaluates on every foreground change.
- **Google Messages was watched.** It declares the social category, like the feed apps.

Nudgi has one user, its developer, who is fine with more notifications while the data is collected.

## Decision

- **The holdout goes from 10% to 50%.** Half of the nudges that would be shown are withheld and recorded as
  suppressed, still after the cooldown and still never for a snooze follow-up. A 50/50 split is the most
  informative for comparing nudging with not nudging.
- **No daily cap.** The cooldown between two nudges and the once-per-level rules already bound how often a nudge
  can come. `SuppressionReason.DailyCap` stays so older rows decode, and is no longer produced.
- **Messaging apps are never watched**, starting with Google Messages, whatever category they declare.
- The policy becomes `rules_v3` (see [0017](0017-policy-id-on-nudge-decisions.md)).

## Consequences

- About half as many nudges reach the user for the same use. That is the price of a control group; the bandit is
  expected to find which half deserved one.
- Without the cap, the late-night rule always gets its chance, and friction can climb the whole ladder in a day.
- Cooldown suppressions are still recorded again at every evaluation while the cooldown lasts. The bandit's data
  preparation deduplicates them (see 0010), and the flood the cap produced is gone.
- These settings suit a single consenting user collecting data. Before Nudgi has other users, the holdout and a
  cap need to be decided again.
