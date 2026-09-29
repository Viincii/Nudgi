package com.vincentmignot.nudgi.core.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_START = 1_000_000_000L
private const val NOW = DAY_START + 12 * 60 * MINUTE_MS

class FrictionTest {
    private val config = NudgeConfig()
    private val followUp = NudgeCandidate(NudgeRule.SnoozeFollowUp, level = 1, thresholdMs = 5 * MINUTE_MS)
    private val longSession = NudgeCandidate(NudgeRule.LongSession, level = 1, thresholdMs = 20 * MINUTE_MS)

    private var nextId = 0

    /** A shown nudge on [FEED] at [level], [minutesAgo] before [NOW]. */
    private fun nudge(
        level: FrictionLevel,
        minutesAgo: Long,
        response: NudgeResponse? = NudgeResponse.Snooze,
        shown: Boolean = true,
        packageName: String = FEED,
    ): PastNudge {
        val timestamp = NOW - minutesAgo * MINUTE_MS
        return PastNudge(
            nudgeId = "n${nextId++}",
            timestamp = timestamp,
            packageName = packageName,
            rule = NudgeRule.SnoozeFollowUp,
            level = 1,
            shown = shown,
            response = response,
            respondedAt = response?.let { timestamp + MINUTE_MS },
            frictionLevel = level,
        )
    }

    private fun context(
        vararg past: PastNudge,
        pausedUntil: Long? = null,
    ) = NudgeContext(
        now = NOW,
        packageName = FEED,
        sessionStartedAt = NOW - 40 * MINUTE_MS,
        dayStartedAt = DAY_START,
        nightStartedAt = null,
        dailyUsageMs = 40 * MINUTE_MS,
        lateNightUsageMs = 0,
        localHour = 12,
        weekday = 3,
        pastNudges = past.toList(),
        frictionPausedUntil = pausedUntil,
    )

    private fun decide(
        context: NudgeContext,
        candidate: NudgeCandidate = followUp,
        draw: Double = 0.5,
    ) = decideFriction(context, candidate, config, draw)

    @Test
    fun `a day without snoozes stays on notifications`() {
        val friction = decide(context(), candidate = longSession)

        assertEquals(FrictionLevel.Notification, friction.requested)
        assertEquals(FrictionLevel.Notification, friction.applied)
    }

    @Test
    fun `one snooze is not enough to escalate`() {
        val friction = decide(context(nudge(FrictionLevel.Notification, 10)))

        assertEquals(FrictionLevel.Notification, friction.applied)
    }

    @Test
    fun `the second snooze at a level escalates the follow-up`() {
        val ctx = context(nudge(FrictionLevel.Notification, 20), nudge(FrictionLevel.Notification, 10))

        assertEquals(FrictionLevel.Overlay, decide(ctx).applied)
    }

    @Test
    fun `only a snooze follow-up escalates, other rules fire at the level reached`() {
        val ctx =
            context(
                nudge(FrictionLevel.Notification, 40),
                nudge(FrictionLevel.Notification, 30),
                nudge(FrictionLevel.Overlay, 20, response = NudgeResponse.Stop),
            )

        val twoSnoozes = ctx.copy(pastNudges = ctx.pastNudges.take(2))
        assertEquals(FrictionLevel.Notification, decide(twoSnoozes, candidate = longSession).applied)
        assertEquals(FrictionLevel.Overlay, decide(ctx, candidate = longSession).applied)
    }

    @Test
    fun `stopping never raises the level`() {
        val ctx =
            context(
                nudge(FrictionLevel.Notification, 20, response = NudgeResponse.Stop),
                nudge(FrictionLevel.Notification, 10, response = NudgeResponse.Stop),
            )

        assertEquals(FrictionLevel.Notification, decide(ctx).applied)
    }

    @Test
    fun `snoozes at a lower level do not count toward the next one`() {
        val ctx =
            context(
                nudge(FrictionLevel.Notification, 40),
                nudge(FrictionLevel.Notification, 30),
                nudge(FrictionLevel.Overlay, 20),
            )

        assertEquals(FrictionLevel.Overlay, decide(ctx).applied)
    }

    @Test
    fun `an escalation held out stays at the current level and asks again next time`() {
        val ctx = context(nudge(FrictionLevel.Notification, 20), nudge(FrictionLevel.Notification, 10))

        val heldOut = decide(ctx, draw = 0.85)

        assertEquals(FrictionLevel.Overlay, heldOut.requested)
        assertEquals(FrictionLevel.Notification, heldOut.applied)
        val next = context(*(ctx.pastNudges + nudge(FrictionLevel.Notification, 5)).toTypedArray())
        assertEquals(FrictionLevel.Overlay, decide(next).requested)
    }

    @Test
    fun `a held-out nudge does not raise the level`() {
        val ctx = context(nudge(FrictionLevel.Overlay, 10, shown = false))

        assertEquals(FrictionLevel.Notification, frictionState(ctx).level)
    }

    @Test
    fun `the ladder climbs to a forced close, then starts again from the countdown`() {
        val beforeClose =
            context(
                nudge(FrictionLevel.CountdownOverlay, 20),
                nudge(FrictionLevel.CountdownOverlay, 10),
            )
        assertEquals(FrictionLevel.ForcedClose, decide(beforeClose).applied)

        val afterClose =
            context(*(beforeClose.pastNudges + nudge(FrictionLevel.ForcedClose, 5, response = null)).toTypedArray())
        assertEquals(FrictionState(FrictionLevel.CountdownOverlay, snoozesAtLevel = 0), frictionState(afterClose))
        assertEquals(FrictionLevel.CountdownOverlay, decide(afterClose, candidate = longSession).applied)
    }

    @Test
    fun `the ladder is per app and per day`() {
        val otherApp = nudge(FrictionLevel.Overlay, 10, packageName = OTHER)
        val yesterday = nudge(FrictionLevel.Overlay, 13 * 60)

        assertEquals(FrictionLevel.Notification, frictionState(context(otherApp, yesterday)).level)
    }

    @Test
    fun `a pause caps the applied level but keeps the ladder`() {
        val ctx =
            context(
                nudge(FrictionLevel.Notification, 40),
                nudge(FrictionLevel.Notification, 30),
                nudge(FrictionLevel.Overlay, 20, response = NudgeResponse.Stop),
                pausedUntil = NOW + 30 * MINUTE_MS,
            )

        val friction = decide(ctx, candidate = longSession)

        assertTrue(friction.paused)
        assertEquals(FrictionLevel.Overlay, friction.requested)
        assertEquals(FrictionLevel.Notification, friction.applied)
    }

    @Test
    fun `an ended pause no longer applies`() {
        val friction = decide(context(pausedUntil = NOW - MINUTE_MS), candidate = longSession)

        assertFalse(friction.paused)
    }
}
