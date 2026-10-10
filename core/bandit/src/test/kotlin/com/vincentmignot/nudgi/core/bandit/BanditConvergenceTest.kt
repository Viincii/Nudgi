package com.vincentmignot.nudgi.core.bandit

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The bandit against a small simulated user whose benefits are linear in the features, so the
 * model is right by construction (decision 0022). It acts online: each choice is observed and
 * learned from before the next. If its regret does not fall far below that of choosing at random,
 * the maths is wrong. The long runs, over many seeds and realistic scenarios, live in
 * `tools:simulator`.
 */
class BanditConvergenceTest {
    /**
     * Doing nothing leaves the feeds 30% of the time; a notification only helps at night, and every
     * higher level helps less than it. During the day the best action is then nothing, since a
     * notification does not pay its cost; at night it is the notification.
     */
    private fun trueBenefit(
        context: BanditContext,
        action: BanditAction,
    ): Double {
        val night = if (RewardV1.isNight(context.localHour)) 1.0 else 0.0
        return when (action) {
            BanditAction.Nothing -> 0.3
            BanditAction.Notification -> 0.3 + 0.35 * night
            BanditAction.Overlay -> 0.3 + 0.2 * night
            BanditAction.CountdownOverlay -> 0.3 + 0.1 * night
            BanditAction.ForcedClose -> 0.3
        }
    }

    private fun trueReward(
        context: BanditContext,
        action: BanditAction,
    ): Double = RewardV1.reward(action, context.localHour, trueBenefit(context, action))

    /** What choosing [action] loses against the best action of [context], in expected reward. */
    private fun regret(
        context: BanditContext,
        action: BanditAction,
    ): Double = BanditAction.entries.maxOf { trueReward(context, it) } - trueReward(context, action)

    private fun randomContext(random: Random): BanditContext {
        // Mostly evenings and nights, as on the real device.
        val hour = listOf(13, 18, 20, 21, 22, 23, 0, 0, 1, 2).random(random)
        return BanditContext(
            localHour = hour,
            weekday = random.nextInt(1, 8),
            sessionMs = random.nextLong(5, 60) * 60_000L,
            dailyMs = random.nextLong(30, 240) * 60_000L,
            lateNightMs = if (RewardV1.isNight(hour)) random.nextLong(0, 60) * 60_000L else 0,
            nudgesToday = random.nextInt(0, 8),
            msSinceLastNudge = random.nextLong(5, 180) * 60_000L,
            snoozesToday = random.nextInt(0, 3),
            frictionLevelReached = random.nextInt(0, 4),
            ruleId = listOf("late_night", "long_session", "daily_budget").random(random),
        )
    }

    /**
     * The night is learned within a few hundred decisions, the day much later: there the
     * notification gains exactly its cost over doing nothing, and Thompson sampling keeps exploring
     * a gap that small. Over 20 seeds the ratio below ends between 0.08 and 0.18.
     */
    @Test
    fun `its regret falls far below that of a random choice in a linear world`() {
        val random = Random(2026)
        val bandit = LinearThompsonSampling(propensityDraws = 1)
        val history = mutableListOf<Observation>()
        var banditRegret = 0.0
        var randomRegret = 0.0

        repeat(DECISIONS) { step ->
            val context = randomContext(random)
            val action = bandit.choose(context, BanditAction.entries.toSet(), history, random).action
            val benefit = trueBenefit(context, action) + NOISE_SD * random.nextGaussian()
            history += Observation(context, action, benefit)
            if (step >= DECISIONS - SCORED) {
                banditRegret += regret(context, action)
                randomRegret += BanditAction.entries.sumOf { regret(context, it) } / BanditAction.entries.size
            }
        }

        val ratio = banditRegret / randomRegret
        assertTrue("regret ${"%.3f".format(ratio)} of a random choice's", ratio < 0.25)
    }

    private companion object {
        const val DECISIONS = 1000
        const val SCORED = 200
        const val NOISE_SD = 0.2
    }
}
