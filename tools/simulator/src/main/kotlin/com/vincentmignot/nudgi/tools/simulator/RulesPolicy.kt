package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import kotlin.random.Random

/**
 * The rules of `core:nudge` once a rule has fired, as a probability over actions: held out with
 * [holdoutProbability] except on a snooze follow-up, otherwise shown at the level reached, and at
 * the requested level above it with [escalationProbability]. The values are those of `NudgeConfig`;
 * pauses and the fallback to a notification are left out.
 */
class RulesPolicy(
    val holdoutProbability: Double = 0.5,
    val escalationProbability: Double = 0.8,
) {
    fun distribution(decision: SimDecision): Map<BanditAction, Double> {
        val show = if (decision.isFollowUp) 1.0 else 1.0 - holdoutProbability
        val reached = decision.context.frictionLevelReached
        val requested = decision.requestedLevel
        val shown =
            if (requested > reached) {
                mapOf(
                    frictionAction(requested) to escalationProbability,
                    frictionAction(reached) to 1 - escalationProbability,
                )
            } else {
                mapOf(frictionAction(reached) to 1.0)
            }
        return buildMap {
            if (show < 1.0) put(BanditAction.Nothing, 1 - show)
            shown.forEach { (action, probability) -> put(action, show * probability) }
        }
    }

    fun sample(
        decision: SimDecision,
        random: Random,
    ): BanditAction {
        var draw = random.nextDouble()
        val distribution = distribution(decision)
        for ((action, probability) in distribution) {
            draw -= probability
            if (draw < 0) return action
        }
        return distribution.keys.last()
    }
}
