package com.vincentmignot.nudgi.core.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val APP = "com.example.feed"
private const val MINUTE = 60_000L
private const val DAY_START = 1_000_000_000L

class NudgeRulesTest {
    private val config = NudgeConfig()

    private fun context(
        sessionMs: Long = 0,
        dailyUsageMs: Long = sessionMs,
        lateNightUsageMs: Long = 0,
        nightStartedAt: Long? = null,
        pastNudges: List<PastNudge> = emptyList(),
        now: Long = DAY_START + 12 * 60 * MINUTE,
    ) = NudgeContext(
        now = now,
        packageName = APP,
        sessionStartedAt = now - sessionMs,
        dayStartedAt = DAY_START,
        nightStartedAt = nightStartedAt,
        dailyUsageMs = dailyUsageMs,
        lateNightUsageMs = lateNightUsageMs,
        localHour = 12,
        weekday = 3,
        pastNudges = pastNudges,
    )

    private fun past(
        rule: NudgeRule,
        timestamp: Long,
        level: Int = 1,
        shown: Boolean = true,
        response: NudgeResponse? = null,
        respondedAt: Long? = null,
        id: String = "nudge-$timestamp",
    ) = PastNudge(id, timestamp, APP, rule, level, shown, response, respondedAt)

    @Test
    fun `no rule fires for a short session`() {
        assertNull(nudgeCandidate(context(sessionMs = 19 * MINUTE), config))
    }

    @Test
    fun `long session fires level 1 at 20 minutes, then one level every 15 minutes`() {
        assertEquals(
            NudgeCandidate(NudgeRule.LongSession, level = 1, thresholdMs = 20 * MINUTE),
            nudgeCandidate(context(sessionMs = 20 * MINUTE), config),
        )
        assertEquals(
            NudgeCandidate(NudgeRule.LongSession, level = 3, thresholdMs = 50 * MINUTE),
            nudgeCandidate(context(sessionMs = 52 * MINUTE), config),
        )
    }

    @Test
    fun `long session does not fire again for a level already handled in this session`() {
        val ctx = context(sessionMs = 30 * MINUTE)
        val handled = past(NudgeRule.LongSession, timestamp = ctx.now - 10 * MINUTE, level = 1)

        assertNull(nudgeCandidate(ctx.copy(pastNudges = listOf(handled)), config))
    }

    @Test
    fun `a long session nudge from a previous session does not count`() {
        val ctx = context(sessionMs = 25 * MINUTE)
        val previousSession = past(NudgeRule.LongSession, timestamp = ctx.sessionStartedAt - MINUTE)

        assertEquals(
            NudgeRule.LongSession,
            nudgeCandidate(ctx.copy(pastNudges = listOf(previousSession)), config)?.rule,
        )
    }

    @Test
    fun `a held-out nudge counts as handled`() {
        val ctx = context(sessionMs = 30 * MINUTE)
        val heldOut = past(NudgeRule.LongSession, timestamp = ctx.now - 10 * MINUTE, shown = false)

        assertNull(nudgeCandidate(ctx.copy(pastNudges = listOf(heldOut)), config))
    }

    @Test
    fun `daily budget fires once per threshold crossed today`() {
        val ctx = context(sessionMs = 5 * MINUTE, dailyUsageMs = 95 * MINUTE)
        assertEquals(
            NudgeCandidate(NudgeRule.DailyBudget, level = 2, thresholdMs = 90 * MINUTE),
            nudgeCandidate(ctx, config),
        )

        val handled = past(NudgeRule.DailyBudget, timestamp = DAY_START + MINUTE, level = 2)
        assertNull(nudgeCandidate(ctx.copy(pastNudges = listOf(handled)), config))

        val yesterday = past(NudgeRule.DailyBudget, timestamp = DAY_START - MINUTE, level = 2)
        assertEquals(NudgeRule.DailyBudget, nudgeCandidate(ctx.copy(pastNudges = listOf(yesterday)), config)?.rule)
    }

    @Test
    fun `late night fires once per night and wins over the other rules`() {
        val nightStart = DAY_START + 23 * 60 * MINUTE
        val ctx =
            context(
                sessionMs = 40 * MINUTE,
                lateNightUsageMs = 10 * MINUTE,
                nightStartedAt = nightStart,
                now = nightStart + 40 * MINUTE,
            )
        assertEquals(NudgeRule.LateNight, nudgeCandidate(ctx, config)?.rule)

        val tonight = past(NudgeRule.LateNight, timestamp = nightStart + 12 * MINUTE)
        assertEquals(NudgeRule.LongSession, nudgeCandidate(ctx.copy(pastNudges = listOf(tonight)), config)?.rule)
    }

    @Test
    fun `late night needs the usage threshold inside the window`() {
        val nightStart = DAY_START + 23 * 60 * MINUTE
        val ctx = context(lateNightUsageMs = 9 * MINUTE, nightStartedAt = nightStart, now = nightStart + 9 * MINUTE)

        assertNull(nudgeCandidate(ctx, config))
    }

    @Test
    fun `snooze follow-up fires five minutes after the snooze, in the same session`() {
        val ctx = context(sessionMs = 30 * MINUTE)
        val snoozed =
            past(
                NudgeRule.LongSession,
                timestamp = ctx.now - 6 * MINUTE,
                response = NudgeResponse.Snooze,
                respondedAt = ctx.now - 5 * MINUTE,
                id = "snoozed",
            )

        assertEquals(
            NudgeCandidate(NudgeRule.SnoozeFollowUp, level = 1, thresholdMs = 5 * MINUTE, followUpOf = "snoozed"),
            nudgeCandidate(ctx.copy(pastNudges = listOf(snoozed)), config),
        )
    }

    @Test
    fun `snooze follow-up waits for the delay and is dropped once the user left the app`() {
        val ctx = context(sessionMs = 30 * MINUTE)
        val tooEarly =
            past(
                NudgeRule.LongSession,
                ctx.now - 5 * MINUTE,
                response = NudgeResponse.Snooze,
                respondedAt =
                    ctx.now - 4 * MINUTE,
            )
        val beforeThisSession =
            past(
                NudgeRule.LongSession,
                ctx.sessionStartedAt - 10 * MINUTE,
                response = NudgeResponse.Snooze,
                respondedAt = ctx.sessionStartedAt - 9 * MINUTE,
            )

        assertEquals(
            NudgeRule.LongSession,
            nudgeCandidate(ctx.copy(pastNudges = listOf(beforeThisSession)), config)?.rule,
        )
        // The snoozed nudge itself handled level 1 of this session, so nothing else fires either.
        assertNull(nudgeCandidate(ctx.copy(pastNudges = listOf(tooEarly)), config))
    }

    @Test
    fun `decide shows the candidate when no guardrail applies`() {
        val decision = decideNudge(context(sessionMs = 20 * MINUTE), config, holdoutDraw = 0.5)

        assertEquals(NudgeDecision.Show(NudgeCandidate(NudgeRule.LongSession, 1, 20 * MINUTE)), decision)
    }

    @Test
    fun `decide holds out a nudge when the draw falls under the holdout probability`() {
        val decision = decideNudge(context(sessionMs = 20 * MINUTE), config, holdoutDraw = 0.05)

        assertEquals(SuppressionReason.Holdout, (decision as NudgeDecision.Suppress).reason)
    }

    @Test
    fun `decide applies the cooldown after the last shown nudge, before the holdout`() {
        val ctx = context(sessionMs = 20 * MINUTE, dailyUsageMs = 60 * MINUTE)
        val recent = past(NudgeRule.DailyBudget, timestamp = ctx.now - 9 * MINUTE, level = 1)

        val decision = decideNudge(ctx.copy(pastNudges = listOf(recent)), config, holdoutDraw = 0.0)

        assertEquals(SuppressionReason.Cooldown, (decision as NudgeDecision.Suppress).reason)
    }

    @Test
    fun `decide stops at the daily cap`() {
        val ctx = context(sessionMs = 20 * MINUTE)
        val shownToday = (1..8).map { past(NudgeRule.DailyBudget, timestamp = DAY_START + it * MINUTE, id = "n$it") }

        val decision = decideNudge(ctx.copy(pastNudges = shownToday), config, holdoutDraw = 0.5)

        assertEquals(SuppressionReason.DailyCap, (decision as NudgeDecision.Suppress).reason)
    }

    @Test
    fun `a snooze follow-up skips the cooldown and the holdout`() {
        val ctx = context(sessionMs = 30 * MINUTE)
        val snoozed =
            past(
                NudgeRule.LongSession,
                ctx.now - 6 * MINUTE,
                response = NudgeResponse.Snooze,
                respondedAt =
                    ctx.now - 5 * MINUTE,
            )

        val decision = decideNudge(ctx.copy(pastNudges = listOf(snoozed)), config, holdoutDraw = 0.0)

        assertEquals(NudgeRule.SnoozeFollowUp, (decision as NudgeDecision.Show).candidate.rule)
    }

    @Test
    fun `decide returns None when no rule fires`() {
        assertEquals(NudgeDecision.None, decideNudge(context(sessionMs = MINUTE), config, holdoutDraw = 0.5))
    }
}
