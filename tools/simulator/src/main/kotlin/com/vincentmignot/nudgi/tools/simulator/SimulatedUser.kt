package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.BanditContext
import com.vincentmignot.nudgi.core.bandit.RewardV1
import com.vincentmignot.nudgi.core.bandit.UsageInterval
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

private const val MINUTE_MS = 60_000L

/**
 * What the simulated user does after a decision, whatever the action: once they stopped, whether
 * they come back within the window; once they carried on, how long before they leave on their own.
 * The action only changes the probability of stopping, which keeps every scenario readable as
 * "this action makes me stop that often".
 */
data class Aftermath(
    /** Probability of coming back to the feeds within the window, after stopping. */
    val returnProbability: Double = 0.25,
    /** A return comes after a delay uniform between this and the end of the window. */
    val returnAfterMinMinutes: Double = 5.0,
    /** Mean of the exponential time spent carrying on before leaving anyway. */
    val carryOnMeanMinutes: Double = 60.0,
) {
    /** Expected benefit once stopped, in a window of [windowMinutes]. */
    fun benefitIfStopped(windowMinutes: Double): Double {
        // A return after d minutes leaves d of the window off the feeds.
        val returnedShare = (returnAfterMinMinutes + windowMinutes) / (2 * windowMinutes)
        return 1 - returnProbability * (1 - returnedShare)
    }

    /** Expected benefit after carrying on: the window minus the expected time carried on in it. */
    fun benefitIfCarriedOn(windowMinutes: Double): Double {
        val carriedOn = carryOnMeanMinutes * (1 - exp(-windowMinutes / carryOnMeanMinutes))
        return 1 - carriedOn / windowMinutes
    }
}

/**
 * Watched-app use after one simulated decision, in milliseconds from it: what the synthetic export
 * writes as events, and what the benefit is computed from with [RewardV1], as in the app.
 */
data class Outcome(
    val stopped: Boolean,
    val watchedAfter: List<UsageInterval>,
)

/**
 * A user whose reactions are known: for a context and an action, the probability that they stop
 * scrolling. The expected benefit and reward of every action follow exactly, which is what the
 * bandit's choices are measured against.
 */
class SimulatedUser(
    val name: String,
    private val aftermath: Aftermath,
    private val stopProbability: (BanditContext, BanditAction) -> Double,
) {
    fun stopProbability(
        context: BanditContext,
        action: BanditAction,
    ): Double = stopProbability.invoke(context, action).coerceIn(0.0, 1.0)

    fun expectedBenefit(
        context: BanditContext,
        action: BanditAction,
    ): Double {
        val window = windowMinutes(context)
        val stopped = aftermath.benefitIfStopped(window)
        val carriedOn = aftermath.benefitIfCarriedOn(window)
        return carriedOn + stopProbability(context, action) * (stopped - carriedOn)
    }

    fun expectedReward(
        context: BanditContext,
        action: BanditAction,
    ): Double = RewardV1.reward(action, context.localHour, expectedBenefit(context, action))

    fun sample(
        context: BanditContext,
        action: BanditAction,
        random: Random,
    ): Outcome {
        val windowMs = RewardV1.windowMs(context.localHour)
        return if (random.nextDouble() < stopProbability(context, action)) {
            val returns = random.nextDouble() < aftermath.returnProbability
            val back = (aftermath.returnAfterMinMinutes * MINUTE_MS).toLong()
            val watched =
                if (returns) {
                    // Back on the feeds until past the end of the window.
                    listOf(UsageInterval(random.nextLong(back, windowMs), windowMs + 5 * MINUTE_MS))
                } else {
                    emptyList()
                }
            Outcome(stopped = true, watchedAfter = watched)
        } else {
            val carriedOnMs = (-aftermath.carryOnMeanMinutes * ln(1 - random.nextDouble()) * MINUTE_MS).toLong()
            // Past the window the time carried on no longer counts; capped, the export stays plausible.
            val endMs = carriedOnMs.coerceIn(1, windowMs + 5 * MINUTE_MS)
            Outcome(stopped = false, watchedAfter = listOf(UsageInterval(0, endMs)))
        }
    }

    /** The benefit of [outcome], computed by the app's own reward function. */
    fun benefit(
        context: BanditContext,
        outcome: Outcome,
    ): Double = RewardV1.benefit(decidedAt = 0, localHour = context.localHour, watched = outcome.watchedAfter)

    private fun windowMinutes(context: BanditContext): Double =
        RewardV1.windowMs(context.localHour).toDouble() / MINUTE_MS
}
