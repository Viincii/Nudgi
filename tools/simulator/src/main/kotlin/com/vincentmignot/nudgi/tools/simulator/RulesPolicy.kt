package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import kotlin.random.Random

private const val FORCED_CLOSE_LEVEL = 3

/**
 * The rules of `core:nudge` once a rule has fired, as a probability over actions: held out with
 * [holdoutProbability] except on a snooze follow-up, otherwise shown at the level reached, and at
 * the requested level above it with [escalationProbability]. The defaults are those of
 * `NudgeConfig` (`rules_v4`); pauses and the fallback to a notification are left out.
 *
 * The exploration being considered for `rules_v5` comes on top: a shown nudge moves to a
 * neighbouring level, one up or one down, with [neighbourProbability], and becomes a forced close
 * with [forcedCloseProbability]. It gives every action the bandit may choose a chance under the
 * rules, which both evaluating and training the bandit need.
 */
data class RulesPolicy(
    val holdoutProbability: Double = 0.5,
    val escalationProbability: Double = 0.8,
    val neighbourProbability: Double = 0.0,
    val forcedCloseProbability: Double = 0.0,
) {
    val explores: Boolean get() = neighbourProbability > 0 || forcedCloseProbability > 0

    fun distribution(decision: SimDecision): Map<BanditAction, Double> {
        val show = if (decision.isFollowUp) 1.0 else 1.0 - holdoutProbability
        val levels = DoubleArray(FORCED_CLOSE_LEVEL + 1)
        ladderLevels(decision).forEach { (level, probability) -> explore(level, probability, levels) }
        return buildMap {
            if (show < 1.0) put(BanditAction.Nothing, 1 - show)
            levels.forEachIndexed { level, probability ->
                if (probability > 0) put(frictionAction(level), show * probability)
            }
        }
    }

    /** The actions the rules can take for [decision]: the only ones a bandit can be judged on. */
    fun support(decision: SimDecision): Set<BanditAction> = distribution(decision).keys

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

    /** The level the ladder applies: the one reached, or the requested one above it on an escalation. */
    private fun ladderLevels(decision: SimDecision): Map<Int, Double> {
        val reached = decision.context.frictionLevelReached
        val requested = decision.requestedLevel
        return if (requested > reached) {
            mapOf(requested to escalationProbability, reached to 1 - escalationProbability)
        } else {
            mapOf(reached to 1.0)
        }
    }

    /** Spreads [probability] of showing [level] over the level itself, its neighbours and a forced close. */
    private fun explore(
        level: Int,
        probability: Double,
        levels: DoubleArray,
    ) {
        val neighbours = listOf(level - 1, level + 1).filter { it in 0..FORCED_CLOSE_LEVEL }
        val forcedClose = if (level < FORCED_CLOSE_LEVEL) forcedCloseProbability else 0.0
        neighbours.forEach { levels[it] += probability * neighbourProbability / neighbours.size }
        levels[FORCED_CLOSE_LEVEL] += probability * forcedClose
        levels[level] += probability * (1 - neighbourProbability - forcedClose)
    }

    companion object {
        val V4 = RulesPolicy()

        /** The starting point for `rules_v5` agreed on before simulating it. */
        val V5 = RulesPolicy(holdoutProbability = 0.3, neighbourProbability = 0.2, forcedCloseProbability = 0.05)
    }
}
