package com.vincentmignot.nudgi.core.bandit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val DAY =
    BanditContext(
        localHour = 14,
        weekday = 2,
        sessionMs = 20 * 60_000L,
        dailyMs = 60 * 60_000L,
        lateNightMs = 0,
        nudgesToday = 2,
        msSinceLastNudge = 30 * 60_000L,
        snoozesToday = 0,
        frictionLevelReached = 0,
        ruleId = "long_session",
    )

private val NIGHT = DAY.copy(localHour = 1, lateNightMs = 20 * 60_000L, ruleId = "late_night")

class LinearThompsonSamplingTest {
    private val bandit = LinearThompsonSampling()
    private val random = Random(42)

    private fun observations(
        action: BanditAction,
        context: BanditContext,
        benefit: Double,
        count: Int = 60,
    ) = List(count) { Observation(context, action, benefit) }

    @Test
    fun `without data every allowed action has a chance`() {
        val counts =
            List(300) { bandit.choose(DAY, BanditAction.entries.toSet(), emptyList(), random).action }
                .groupingBy { it }
                .eachCount()

        assertEquals(BanditAction.entries.toSet(), counts.keys)
    }

    @Test
    fun `learns the action with the best reward and chooses it with a high propensity`() {
        val history =
            observations(BanditAction.Nothing, DAY, benefit = 0.2) +
                observations(BanditAction.Notification, DAY, benefit = 0.7)

        val choice = bandit.choose(DAY, setOf(BanditAction.Nothing, BanditAction.Notification), history, random)

        assertEquals(BanditAction.Notification, choice.action)
        assertTrue("propensity ${choice.propensity}", choice.propensity > 0.95)
        assertEquals(120, choice.trainedOn)
    }

    /**
     * How often the bandit nudges when a notification gains [gain] over doing nothing in [context].
     * Thompson sampling stays uncertain on a small gap, so this is a rate, not a single choice.
     */
    private fun notificationRate(
        context: BanditContext,
        gain: Double,
    ): Double {
        val history =
            observations(BanditAction.Nothing, context, benefit = 0.5, count = 3000) +
                observations(BanditAction.Notification, context, benefit = 0.5 + gain, count = 3000)
        val allowed = setOf(BanditAction.Nothing, BanditAction.Notification)
        return List(200) { bandit.choose(context, allowed, history, random).action }
            .count { it == BanditAction.Notification } / 200.0
    }

    @Test
    fun `a gain smaller than the cost keeps the bandit from nudging during the day`() {
        val rate = notificationRate(DAY, gain = 0.08)

        assertTrue("rate $rate", rate < 0.1)
    }

    @Test
    fun `the same small gain is worth a nudge at night`() {
        val rate = notificationRate(NIGHT, gain = 0.08)

        assertTrue("rate $rate", rate > 0.9)
    }

    @Test
    fun `never chooses an action that is not allowed`() {
        val allowed = setOf(BanditAction.Notification, BanditAction.Overlay)

        repeat(100) { assertTrue(bandit.choose(DAY, allowed, emptyList(), random).action in allowed) }
    }
}
