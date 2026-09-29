package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.database.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowBanditTest {
    private val config = NudgeConfig()
    private val start = at("2026-09-22T12:00:00")
    private val watched: (String) -> Boolean = { it == FEED }
    private val longSession = NudgeCandidate(NudgeRule.LongSession, 1, 20 * MINUTE_MS)

    private fun contextAt(
        now: Long,
        sessionMs: Long = 20 * MINUTE_MS,
    ) = NudgeContext(
        now = now,
        packageName = FEED,
        sessionStartedAt = now - sessionMs,
        dayStartedAt = at("2026-09-22T00:00:00"),
        nightStartedAt = null,
        dailyUsageMs = sessionMs,
        lateNightUsageMs = 0,
        localHour = 12,
        weekday = 2,
        pastNudges = emptyList(),
    )

    private fun decision(
        at: Long,
        decision: NudgeDecision,
        friction: FrictionDecision = NOTIFICATION,
        id: String = "n$at",
        sessionMs: Long = 20 * MINUTE_MS,
    ): EventEntity = decisionEvent(decision, friction, contextAt(at, sessionMs), config, id)

    private fun observe(
        events: List<EventEntity>,
        now: Long = start + 3 * 60 * MINUTE_MS,
    ) = banditObservations(events.sortedBy { it.timestamp }, now, PARIS, watched, config)

    @Test
    fun `a shown nudge took its friction level, a held-out one did nothing`() {
        val overlay = FrictionDecision(FrictionLevel.Overlay, FrictionLevel.Overlay, paused = false)
        val events =
            listOf(
                decision(start, NudgeDecision.Show(longSession)),
                decision(start + MINUTE_MS, NudgeDecision.Show(longSession), friction = overlay),
                decision(start + 2 * MINUTE_MS, NudgeDecision.Suppress(longSession, SuppressionReason.Holdout)),
            )

        assertEquals(
            listOf(BanditAction.Notification, BanditAction.Overlay, BanditAction.Nothing),
            observe(events).map { it.action },
        )
    }

    @Test
    fun `benefit is the time off watched apps in the window, a session still running included`() {
        val events =
            listOf(foreground(start - 20 * MINUTE_MS), decision(start, NudgeDecision.Show(longSession))) +
                listOf(background(start + 6 * MINUTE_MS, 26 * MINUTE_MS), foreground(start + 25 * MINUTE_MS))

        val observation = observe(events, now = start + 32 * MINUTE_MS).single()

        assertEquals(1 - 11.0 / 30, observation.benefit, 1e-9)
    }

    @Test
    fun `only decisions whose window has closed are learned from`() {
        val events = listOf(decision(start, NudgeDecision.Show(longSession)))

        assertEquals(0, observe(events, now = start + 30 * MINUTE_MS).size)
        assertEquals(1, observe(events, now = start + 31 * MINUTE_MS).size)
    }

    @Test
    fun `repeated cooldown suppressions count once, other suppressions not at all`() {
        val cooldown = NudgeDecision.Suppress(longSession, SuppressionReason.Cooldown)
        val events =
            listOf(
                decision(start, cooldown),
                decision(start + MINUTE_MS, cooldown, sessionMs = 21 * MINUTE_MS),
                decision(start + 2 * MINUTE_MS, NudgeDecision.Suppress(longSession, SuppressionReason.DailyCap)),
                decision(
                    start + 3 * MINUTE_MS,
                    NudgeDecision.Suppress(longSession, SuppressionReason.NotificationsDisabled),
                ),
            )

        assertEquals(listOf(BanditAction.Nothing), observe(events).map { it.action })
    }

    @Test
    fun `snoozes are derived from the events for rows that did not record them`() {
        val legacy =
            decision(start + 10 * MINUTE_MS, NudgeDecision.Show(longSession)).let { event ->
                val metadata = NudgeJson.decodeFromString<NudgeDecisionMetadata>(event.metadata)
                event.copy(
                    metadata =
                        NudgeJson.encodeToString(
                            metadata.copy(
                                context = metadata.context.copy(snoozesToday = null, frictionLevelReached = null),
                            ),
                        ),
                )
            }
        val events =
            listOf(
                responseEvent("a", FEED, NudgeResponse.Snooze, start),
                responseEvent("b", FEED, NudgeResponse.Stop, start + MINUTE_MS),
                responseEvent("c", OTHER, NudgeResponse.Snooze, start + 2 * MINUTE_MS),
                legacy,
            )

        val context = observe(events).single().context

        assertEquals(1, context.snoozesToday)
        assertEquals(0, context.frictionLevelReached)
    }
}
