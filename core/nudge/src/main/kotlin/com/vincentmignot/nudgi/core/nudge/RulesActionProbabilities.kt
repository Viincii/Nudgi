package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.bandit.BanditAction

/**
 * The probability of every action the rules could have taken for [decision], whose friction was
 * decided as [friction], before the draws that took it (decision 0023). This is the propensity
 * off-policy evaluation divides by, and its support is what the shadow bandit may choose among.
 *
 * A cooldown always does nothing. Otherwise the holdout does nothing, except on a snooze follow-up;
 * a shown nudge gets the ladder's level, the escalation applying with its probability, and each of
 * those levels is then explored as [exploreFriction] does. A pause shows every nudge as a
 * notification. A nudge suppressed because notifications were off was decided as a shown one, so
 * it gets the probabilities of one.
 */
fun rulesActionProbabilities(
    decision: NudgeDecision,
    friction: FrictionDecision,
    config: NudgeConfig,
): Map<BanditAction, Double> {
    val candidate = decision.candidate ?: error("No action is taken when no rule fires")
    if (decision is NudgeDecision.Suppress && decision.reason == SuppressionReason.Cooldown) {
        return mapOf(BanditAction.Nothing to 1.0)
    }
    val show = if (candidate.rule == NudgeRule.SnoozeFollowUp) 1.0 else 1.0 - config.holdoutProbability
    val ladder =
        if (friction.requested > friction.reached) {
            mapOf(
                friction.requested to config.escalationProbability,
                friction.reached to 1 - config.escalationProbability,
            )
        } else {
            mapOf(friction.reached to 1.0)
        }
    val levels = DoubleArray(FrictionLevel.entries.size)
    for ((level, probability) in ladder) {
        if (friction.paused) {
            levels[FrictionLevel.Notification.ordinal] += probability
        } else {
            explore(level, probability, levels, config)
        }
    }
    return buildMap {
        if (show < 1.0) put(BanditAction.Nothing, 1.0 - show)
        FrictionLevel.entries.forEach { level ->
            val probability = levels[level.ordinal]
            if (probability > 0.0) put(level.banditAction, show * probability)
        }
    }
}

/** Spreads [probability] of the ladder applying [level] over the levels [exploreFriction] can reach from it. */
private fun explore(
    level: FrictionLevel,
    probability: Double,
    levels: DoubleArray,
    config: NudgeConfig,
) {
    val neighbours = neighbourLevels(level)
    val forcedClose = if (level < FrictionLevel.ForcedClose) config.forcedCloseProbability else 0.0
    neighbours.forEach { levels[it.ordinal] += probability * config.neighbourProbability / neighbours.size }
    levels[FrictionLevel.ForcedClose.ordinal] += probability * forcedClose
    levels[level.ordinal] += probability * (1 - config.neighbourProbability - forcedClose)
}
