package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgeContextBuilderTest {
    private val config = NudgeConfig()
    private val watched: (String) -> Boolean = { it == FEED || it == OTHER }

    private fun build(
        events: List<EventEntity>,
        now: Long,
        isWatched: (String) -> Boolean = watched,
    ) = buildNudgeContext(events.sortedBy { it.timestamp }, now, PARIS, isWatched, config)

    @Test
    fun `no context when the last app went to the background`() {
        val events = closedSession(at("2026-09-22T10:00:00"), at("2026-09-22T10:30:00"))

        assertNull(build(events, now = at("2026-09-22T10:31:00")))
    }

    @Test
    fun `no context when the foreground app is not watched`() {
        val events = listOf(foreground(at("2026-09-22T10:00:00")))

        assertNull(build(events, now = at("2026-09-22T10:30:00"), isWatched = { false }))
    }

    @Test
    fun `merges pieces of the same app separated by an activity switch`() {
        val events =
            closedSession(at("2026-09-22T10:00:00"), at("2026-09-22T10:10:00")) +
                foreground(at("2026-09-22T10:10:02"))

        val context = build(events, now = at("2026-09-22T10:25:00"))!!

        assertEquals(at("2026-09-22T10:00:00"), context.sessionStartedAt)
        assertEquals(25 * MINUTE_MS, context.sessionMs)
    }

    @Test
    fun `starts a new session after a real gap or another app`() {
        val events =
            closedSession(at("2026-09-22T09:00:00"), at("2026-09-22T09:10:00")) +
                closedSession(at("2026-09-22T09:10:01"), at("2026-09-22T09:20:00"), packageName = OTHER) +
                foreground(at("2026-09-22T09:20:01"))

        val context = build(events, now = at("2026-09-22T09:30:00"))!!

        assertEquals(at("2026-09-22T09:20:01"), context.sessionStartedAt)
    }

    @Test
    fun `daily usage counts today's closed sessions of the app plus the running one`() {
        val events =
            closedSession(at("2026-09-21T23:50:00"), at("2026-09-22T00:10:00")) +
                closedSession(at("2026-09-22T08:00:00"), at("2026-09-22T08:30:00")) +
                closedSession(at("2026-09-22T09:00:00"), at("2026-09-22T09:45:00"), packageName = OTHER) +
                foreground(at("2026-09-22T12:00:00"))

        val context = build(events, now = at("2026-09-22T12:05:00"))!!

        assertEquals((10 + 30 + 5) * MINUTE_MS, context.dailyUsageMs)
        assertEquals(12, context.localHour)
        assertEquals(2, context.weekday)
        assertNull(context.nightStartedAt)
    }

    @Test
    fun `late-night usage counts every watched app since 23h, across midnight`() {
        val events =
            closedSession(at("2026-09-22T22:50:00"), at("2026-09-22T23:05:00"), packageName = OTHER) +
                foreground(at("2026-09-23T00:30:00"))

        val context = build(events, now = at("2026-09-23T00:36:00"))!!

        assertEquals(at("2026-09-22T23:00:00"), context.nightStartedAt)
        assertEquals((5 + 6) * MINUTE_MS, context.lateNightUsageMs)
    }

    @Test
    fun `pastNudges keeps shown and held-out nudges with their responses, not other suppressions`() {
        val now = at("2026-09-22T12:00:00")
        val decisionContext =
            NudgeContext(now, FEED, now - 20 * MINUTE_MS, at("2026-09-22T00:00:00"), null, 0, 0, 12, 2, emptyList())
        val candidate = NudgeCandidate(NudgeRule.LongSession, 1, 20 * MINUTE_MS)
        val events =
            listOf(
                decisionEvent(NudgeDecision.Show(candidate), decisionContext, config, "shown"),
                decisionEvent(
                    NudgeDecision.Suppress(candidate, SuppressionReason.Holdout),
                    decisionContext,
                    config,
                    "held-out",
                ),
                decisionEvent(
                    NudgeDecision.Suppress(candidate, SuppressionReason.Cooldown),
                    decisionContext,
                    config,
                    "cooldown",
                ),
                responseEvent("shown", FEED, NudgeResponse.Snooze, now + MINUTE_MS),
            )

        val past = pastNudges(events)

        assertEquals(listOf("shown", "held-out"), past.map { it.nudgeId })
        assertEquals(listOf(true, false), past.map { it.shown })
        assertEquals(NudgeResponse.Snooze, past[0].response)
        assertEquals(now + MINUTE_MS, past[0].respondedAt)
    }
}
