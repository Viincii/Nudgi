package com.vincentmignot.nudgi.core.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEvaluationTest {
    private val config = NudgeConfig()
    private val noon = at("2026-09-22T12:00:00")

    private fun context(
        sessionMs: Long,
        dailyUsageMs: Long = sessionMs,
        now: Long = noon,
        nightStartedAt: Long? = null,
        lateNightUsageMs: Long = 0,
        pastNudges: List<PastNudge> = emptyList(),
    ) = NudgeContext(
        now = now,
        packageName = FEED,
        sessionStartedAt = now - sessionMs,
        dayStartedAt = at("2026-09-22T00:00:00"),
        nightStartedAt = nightStartedAt,
        dailyUsageMs = dailyUsageMs,
        lateNightUsageMs = lateNightUsageMs,
        localHour = 12,
        weekday = 2,
        pastNudges = pastNudges,
    )

    private fun shown(
        rule: NudgeRule,
        timestamp: Long,
        level: Int = 1,
        response: NudgeResponse? = null,
        respondedAt: Long? = null,
    ) = PastNudge("n-$timestamp", timestamp, FEED, rule, level, shown = true, response, respondedAt)

    @Test
    fun `wakes up when the session reaches the first long-session threshold`() {
        assertEquals(noon + 15 * MINUTE_MS, nextEvaluationAt(context(sessionMs = 5 * MINUTE_MS), config, PARIS))
    }

    @Test
    fun `wakes up for the next level once one is handled`() {
        val ctx = context(sessionMs = 21 * MINUTE_MS)
        val handled = shown(NudgeRule.LongSession, timestamp = noon - MINUTE_MS)

        assertEquals(
            ctx.sessionStartedAt + 35 * MINUTE_MS,
            nextEvaluationAt(ctx.copy(pastNudges = listOf(handled)), config, PARIS),
        )
    }

    @Test
    fun `wakes up earlier for a daily budget threshold`() {
        val ctx = context(sessionMs = 5 * MINUTE_MS, dailyUsageMs = 57 * MINUTE_MS)

        assertEquals(noon + 3 * MINUTE_MS, nextEvaluationAt(ctx, config, PARIS))
    }

    @Test
    fun `waits for the end of the cooldown`() {
        val ctx = context(sessionMs = 30 * MINUTE_MS, dailyUsageMs = 61 * MINUTE_MS)
        val recent = shown(NudgeRule.LongSession, timestamp = noon - 2 * MINUTE_MS)

        assertEquals(noon + 8 * MINUTE_MS, nextEvaluationAt(ctx.copy(pastNudges = listOf(recent)), config, PARIS))
    }

    @Test
    fun `a snooze follow-up is not delayed by the cooldown`() {
        val ctx = context(sessionMs = 25 * MINUTE_MS)
        val snoozed =
            shown(
                NudgeRule.LongSession,
                timestamp = noon - 2 * MINUTE_MS,
                response = NudgeResponse.Snooze,
                respondedAt = noon - MINUTE_MS,
            )

        assertEquals(noon + 4 * MINUTE_MS, nextEvaluationAt(ctx.copy(pastNudges = listOf(snoozed)), config, PARIS))
    }

    @Test
    fun `counts the late-night threshold from the start of tonight's window`() {
        val evening = at("2026-09-22T22:55:00")
        val ctx = context(sessionMs = MINUTE_MS, now = evening, dailyUsageMs = 0)

        assertEquals(at("2026-09-22T23:10:00"), nextEvaluationAt(ctx, config, PARIS))
    }

    @Test
    fun `never asks to run again sooner than a minute`() {
        val ctx = context(sessionMs = 20 * MINUTE_MS)

        assertEquals(noon + MINUTE_MS, nextEvaluationAt(ctx, config, PARIS))
    }

    @Test
    fun `stops once the daily cap is reached`() {
        val capped = (1..8).map { shown(NudgeRule.DailyBudget, timestamp = at("2026-09-22T08:00:00") + it * MINUTE_MS) }

        assertNull(nextEvaluationAt(context(sessionMs = 5 * MINUTE_MS, pastNudges = capped), config, PARIS))
    }
}
