#!/usr/bin/env python3
"""Scores the nudge decisions of a Nudgi export under the bandit reward, and the shadow bandit against the rules.

Usage: python3 tools/bandit_report.py path/to/nudgi-export.zip

Everything here mirrors the app: the reward is RewardV1 of core:bandit (decision 0019), the decisions the bandit
learns from are those of banditObservations in core:nudge, and the propensities are those of the rules
(decisions 0010, 0016, 0018, 0023). The export does not say which apps were watched, so the script watches the apps the
rules nudged on, plus the known feed apps.

Decisions listed in tools/excluded_decisions.json, by nudge id with the reason, are left out: their outcome was
caused by testing the app rather than by the user.

The shadow bandit is scored by inverse propensity weighting (decision 0020): over the decisions where the rules
happened to take the action the shadow chose, each reward counts 1 / (probability that the rules took it). That is
an unbiased estimate of what the shadow would have scored had it been in charge, without ever letting it act, as long
as the rules could take every action it chooses. Since rules_v5 and lin_ts_v2 they can (decision 0023), so the
estimates only use those decisions, and compare the shadow with the ladder alone: what Nudgi would do with no bandit
and no measurement. Their spread comes from resampling the decisions.
"""

import json
import random
import sys
import zipfile
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

MINUTE_MS = 60_000
MERGE_GAP_MS = MINUTE_MS

KNOWN_FEED_APPS = {
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.instagram.android", "com.google.android.youtube",
    "com.reddit.frontpage", "com.twitter.android", "com.facebook.katana", "com.snapchat.android",
}
NEVER_WATCHED = {"com.google.android.apps.messaging"}

COSTS = {"nothing": 0.0, "notification": 0.1, "overlay": 0.2, "countdown_overlay": 0.3, "forced_close": 0.5}
FRICTION_ACTIONS = ["notification", "overlay", "countdown_overlay", "forced_close"]


def is_night(hour):
    return 0 <= hour < 6


def weight(hour):
    return 2.0 if is_night(hour) else 1.0


def window_ms(hour):
    return 60 * MINUTE_MS if is_night(hour) else 30 * MINUTE_MS


def benefit(t, hour, intervals):
    """Share of the window after t not spent on watched apps, overlaps counted once."""
    end = t + window_ms(hour)
    used, covered = 0, t
    for s, e in sorted((max(s, t), min(e, end)) for s, e in intervals if min(e, end) > max(s, t)):
        start = max(s, covered)
        if e > start:
            used += e - start
        covered = max(covered, e)
    return 1 - used / window_ms(hour)


def load(path):
    path = Path(path)
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as z:
            manifest = json.loads(z.read("manifest.json"))
            events = [json.loads(line) for line in z.read("events.jsonl").decode().splitlines() if line]
    else:
        manifest = json.loads((path / "manifest.json").read_text())
        events = [json.loads(line) for line in (path / "events.jsonl").read_text().splitlines() if line]
    return manifest, sorted(events, key=lambda e: (e["timestamp"], e["id"]))


def watched_intervals(events, watched, now):
    intervals = [
        (e["timestamp"] - e["duration_ms"], e["timestamp"])
        for e in events
        if e["event_type"] == "app_background" and e["package_name"] in watched and e["duration_ms"] > 0
    ]
    # A watched session still open at export time runs until the export.
    last = [e for e in events if e["event_type"] in ("app_foreground", "app_background")]
    if last and last[-1]["event_type"] == "app_foreground" and last[-1]["package_name"] in watched:
        intervals.append((last[-1]["timestamp"], now))
    return intervals


def rules_distribution(md):
    """The probability of every action under the rules for this decision.

    Since rules_v5 it is recorded as action_probabilities. Before, it is recomputed from the holdout and the escalation
    probabilities, the only draws those rules made.
    """
    if md.get("action_probabilities") is not None:
        return md["action_probabilities"]
    if md.get("reason") == "cooldown":
        return {"nothing": 1.0}
    holdout = md.get("holdout_probability", 0.0)
    show = 1.0 if md["rule_id"] == "snooze_followup" else 1.0 - holdout
    distribution = {"nothing": 1.0 - show} if show < 1.0 else {}
    requested, applied = md.get("requested_friction_level"), md.get("friction_level") or 0
    reached = md["context"].get("friction_level_reached")
    escalation = md.get("escalation_probability")
    paused, fallback = md.get("friction_paused"), md.get("friction_fallback")
    if None in (requested, reached, escalation) or requested <= reached or paused or fallback:
        distribution[FRICTION_ACTIONS[applied]] = show
    else:
        distribution[FRICTION_ACTIONS[requested]] = show * escalation
        distribution[FRICTION_ACTIONS[reached]] = show * (1.0 - escalation)
    return distribution


def ladder_action(md):
    """What the ladder alone would have done: no holdout, no exploration, the escalation always applied."""
    if md.get("reason") == "cooldown":
        return "nothing"
    if md.get("friction_paused"):
        return "notification"
    return FRICTION_ACTIONS[md.get("requested_friction_level") or 0]


def excluded_decisions():
    path = Path(__file__).with_name("excluded_decisions.json")
    return set(json.loads(path.read_text())) if path.exists() else set()


def decisions(events, now, watched, excluded):
    """The decisions the bandit learns from, as in banditObservations: closed windows, deduplicated cooldowns."""
    intervals = watched_intervals(events, watched, now)
    seen_cooldowns = set()
    rows = []
    for e in events:
        if e["event_type"] not in ("nudge_shown", "nudge_suppressed"):
            continue
        md, t = e["metadata"], e["timestamp"]
        if md["nudge_id"] in excluded:
            continue
        hour = md["context"]["local_hour"]
        if now < t + window_ms(hour) + MERGE_GAP_MS:
            continue
        if e["event_type"] == "nudge_shown":
            level = md.get("friction_level") or 0
            action = FRICTION_ACTIONS[level]
        elif md.get("reason") == "holdout":
            action = "nothing"
        elif md.get("reason") == "cooldown":
            session_start = t - md["context"]["session_ms"]
            key = (md["rule_id"], md["level"], e["package_name"], session_start // MERGE_GAP_MS)
            if key in seen_cooldowns:
                continue
            seen_cooldowns.add(key)
            action = "nothing"
        else:
            continue
        b = benefit(t, hour, intervals)
        rows.append({
            "t": t, "hour": hour, "rule": md["rule_id"], "policy": md.get("policy_id") or "rules_v1",
            "action": action, "benefit": b, "reward": weight(hour) * b - COSTS[action],
            "distribution": rules_distribution(md), "logged": md.get("action_probabilities") is not None,
            "fallback": md.get("friction_fallback") is not None, "ladder": ladder_action(md),
            "shadow": md.get("shadow"), "expression": md.get("expression"),
        })
    return rows


def mean(values):
    values = list(values)
    return sum(values) / len(values) if values else float("nan")


def estimates(rows, target):
    """IPS and self-normalized IPS of the policy that takes target(row), from what the rules did."""
    weights = [1 / r["distribution"][r["action"]] if target(r) == r["action"] else 0.0 for r in rows]
    weighted = sum(w * r["reward"] for w, r in zip(weights, rows))
    return weighted / len(rows), (weighted / sum(weights) if sum(weights) else float("nan"))


def spread(values):
    m = mean(values)
    return (sum((v - m) ** 2 for v in values) / (len(values) - 1)) ** 0.5 if len(values) > 1 else float("nan")


def print_estimates(rows, resamples=2000):
    shadow, ladder = (lambda r: r["shadow"]["action"]), (lambda r: r["ladder"])
    point = [estimates(rows, shadow), estimates(rows, ladder)]
    draws = random.Random(0)
    boot = []
    for _ in range(resamples):
        sample = draws.choices(rows, k=len(rows))
        boot.append([estimates(sample, shadow), estimates(sample, ladder)])

    def sd(policy, kind):
        return spread([b[policy][kind] for b in boot])

    def sd_difference(kind):
        return spread([b[0][kind] - b[1][kind] for b in boot])

    print(f"  {'':22} {'IPS':>6} {'± sd':>6}   {'self-normalized':>15} {'± sd':>6}")
    for i, name in enumerate(("shadow", "ladder alone")):
        print(f"  {name:22} {point[i][0]:6.2f} {sd(i, 0):6.2f}   {point[i][1]:15.2f} {sd(i, 1):6.2f}")
    difference = [point[0][k] - point[1][k] for k in (0, 1)]
    print(f"  {'shadow − ladder':22} {difference[0]:6.2f} {sd_difference(0):6.2f}   "
          f"{difference[1]:15.2f} {sd_difference(1):6.2f}")
    print(f"  Actual rules reward on the same decisions: {mean(r['reward'] for r in rows):.2f}")
    if difference[0] < -sd_difference(0):
        print("  The shadow is clearly worse than the ladder: it must not go live (decision 0023).")
    else:
        print("  The shadow is not clearly worse than the ladder. That rules out a loss, it does not show a gain:")
        print("  going live also needs the simulator, calibrated on this export, to put it ahead (decision 0023).")


def main(path):
    manifest, events = load(path)
    zone = ZoneInfo(manifest["time_zone"])
    now = manifest["exported_at"]
    nudged = {e["package_name"] for e in events if e["event_type"] in ("nudge_shown", "nudge_suppressed")}
    watched = (nudged | KNOWN_FEED_APPS) - NEVER_WATCHED
    excluded = excluded_decisions()
    rows = decisions(events, now, watched, excluded)

    first, last = (datetime.fromtimestamp(ts / 1000, zone) for ts in (events[0]["timestamp"], events[-1]["timestamp"]))
    print(f"Export: {len(events)} events, {first:%Y-%m-%d %H:%M} to {last:%Y-%m-%d %H:%M}")
    print(f"Decisions with a closed reward window: {len(rows)} ({len(excluded)} excluded by id, if present)")
    print("By policy: " + ", ".join(f"{p} {n}" for p, n in sorted(Counter(r['policy'] for r in rows).items())))

    print("\nWhat each action yielded (reward_v1)")
    print(f"  {'action':18} {'n':>4} {'benefit':>8} {'reward':>8}   day / night n")
    for action in COSTS:
        group = [r for r in rows if r["action"] == action]
        if group:
            night = sum(is_night(r["hour"]) for r in group)
            print(f"  {action:18} {len(group):4} {mean(r['benefit'] for r in group):8.2f} "
                  f"{mean(r['reward'] for r in group):8.2f}   {len(group) - night} / {night}")
    print(f"  {'rules, all':18} {len(rows):4} {mean(r['benefit'] for r in rows):8.2f} {mean(r['reward'] for r in rows):8.2f}")

    faced = [r for r in rows if r["expression"]]
    if faced:
        print("\nWhat each coach expression yielded, on shown interventions (decision 0021)")
        print(f"  {'expression':18} {'n':>4} {'benefit':>8} {'reward':>8}")
        for expression in sorted({r["expression"] for r in faced}):
            group = [r for r in faced if r["expression"] == expression]
            print(f"  {expression:18} {len(group):4} {mean(r['benefit'] for r in group):8.2f} "
                  f"{mean(r['reward'] for r in group):8.2f}")

    shadowed = [r for r in rows if r["shadow"]]
    print(f"\nShadow bandit: {len(shadowed)} scored decisions")
    if not shadowed:
        print("  Nothing to score yet: no decision with a shadow has a closed reward window.")
        return
    print("By shadow policy: " + ", ".join(
        f"{p} {n}" for p, n in sorted(Counter(r["shadow"]["policy_id"] for r in shadowed).items())))
    supported = [r for r in shadowed if r["distribution"].get(r["shadow"]["action"], 0) > 0]
    print(f"  The rules could have taken the shadow's choice {len(supported)} times out of {len(shadowed)} "
          f"({len(supported) / len(shadowed):.0%})")

    # A decision that fell back to a notification did not take the action drawn, so it is left out.
    scored = [r for r in shadowed if r["logged"] and r["shadow"]["policy_id"] != "lin_ts_v1" and not r["fallback"]]
    print(f"\n  Shadow against the ladder alone, over the {len(scored)} decisions of rules_v5 and lin_ts_v2")
    if scored:
        print_estimates(scored)
    else:
        print("  Nothing to estimate yet: no such decision has a closed reward window.")

    print("\n  Rules' action (rows) against the shadow's choice (columns)")
    table = defaultdict(Counter)
    for r in shadowed:
        table[r["action"]][r["shadow"]["action"]] += 1
    short = {a: a.split("_")[0][:8] for a in COSTS}
    print("  " + " " * 18 + "".join(f"{short[a]:>9}" for a in COSTS))
    for a in COSTS:
        if table[a]:
            print(f"  {a:18}" + "".join(f"{table[a][b]:9}" for b in COSTS))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
