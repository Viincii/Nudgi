package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_OUTCOME
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeEventsTest {
    private val config = NudgeConfig()
    private val shownAt = at("2026-09-22T12:00:00")
    private val context =
        NudgeContext(
            now = shownAt,
            packageName = FEED,
            sessionStartedAt = shownAt - 25 * MINUTE_MS,
            dayStartedAt = at("2026-09-22T00:00:00"),
            nightStartedAt = null,
            dailyUsageMs = 70 * MINUTE_MS,
            lateNightUsageMs = 0,
            localHour = 12,
            weekday = 2,
            pastNudges = emptyList(),
        )

    private fun shown(id: String = "n1") =
        decisionEvent(
            NudgeDecision.Show(NudgeCandidate(NudgeRule.LongSession, 1, 20 * MINUTE_MS)),
            NOTIFICATION,
            context,
            config,
            id,
        )

    private fun outcomeOf(event: EventEntity) = NudgeJson.decodeFromString<NudgeOutcomeMetadata>(event.metadata)

    @Test
    fun `decision metadata carries the rule, the propensity and the context`() {
        val candidate = NudgeCandidate(NudgeRule.DailyBudget, 1, 60 * MINUTE_MS)
        val event =
            decisionEvent(
                NudgeDecision.Suppress(candidate, SuppressionReason.Holdout),
                NOTIFICATION,
                context,
                config,
                "n1",
            )
        val json = Json.parseToJsonElement(event.metadata).jsonObject

        assertEquals("nudge_suppressed", event.eventType)
        assertEquals("rules_v4", json.getValue("policy_id").jsonPrimitive.content)
        assertEquals("daily_budget", json.getValue("rule_id").jsonPrimitive.content)
        assertEquals("holdout", json.getValue("reason").jsonPrimitive.content)
        assertEquals("0.5", json.getValue("holdout_probability").jsonPrimitive.content)
        val snapshot = json.getValue("context").jsonObject
        assertEquals("1500000", snapshot.getValue("session_ms").jsonPrimitive.content)
        assertEquals("4200000", snapshot.getValue("daily_ms").jsonPrimitive.content)
        assertFalse("absent values are left out", "ms_since_last_nudge" in snapshot)
    }

    @Test
    fun `decision metadata recorded before policies were identified still decodes`() {
        val legacy =
            """{"nudge_id":"n1","rule_id":"long_session","level":1,"threshold_ms":1200000,""" +
                """"holdout_probability":0.1,"context":{"session_ms":0,"daily_ms":0,"late_night_ms":0,""" +
                """"local_hour":12,"weekday":2,"nudges_today":0}}"""

        assertNull(NudgeJson.decodeFromString<NudgeDecisionMetadata>(legacy).policyId)
    }

    @Test
    fun `decision metadata records the friction requested and applied`() {
        val candidate = NudgeCandidate(NudgeRule.SnoozeFollowUp, 1, 5 * MINUTE_MS)
        val friction =
            FrictionDecision(requested = FrictionLevel.Overlay, applied = FrictionLevel.Notification, paused = false)
                .fallingBack(FrictionFallback.ServiceUnavailable)

        val metadata =
            NudgeJson.decodeFromString<NudgeDecisionMetadata>(
                decisionEvent(NudgeDecision.Show(candidate), friction, context, config, "n1").metadata,
            )

        assertEquals(0, metadata.frictionLevel)
        assertEquals(1, metadata.requestedFrictionLevel)
        assertEquals(0.8, metadata.escalationProbability)
        assertEquals(false, metadata.frictionPaused)
        assertEquals("service_unavailable", metadata.frictionFallback)
    }

    @Test
    fun `outcome waits for the window to close`() {
        val events = listOf(shown())

        assertEquals(emptyList<EventEntity>(), pendingOutcomeEvents(events, shownAt + 10 * MINUTE_MS, config))
    }

    @Test
    fun `outcome records when the user left the app`() {
        val events = listOf(shown(), background(shownAt + 3 * MINUTE_MS, 28 * MINUTE_MS))

        val outcomes = pendingOutcomeEvents(events, shownAt + 12 * MINUTE_MS, config)

        assertEquals(EVENT_TYPE_NUDGE_OUTCOME, outcomes.single().eventType)
        assertTrue(outcomeOf(outcomes.single()).leftApp)
        assertEquals(3 * MINUTE_MS, outcomeOf(outcomes.single()).leftAfterMs)
        assertEquals(false, outcomeOf(outcomes.single()).reopened)
    }

    @Test
    fun `outcome records when the user came back within the window`() {
        val events =
            listOf(
                shown(),
                background(shownAt + 3 * MINUTE_MS, 28 * MINUTE_MS),
                foreground(shownAt + 7 * MINUTE_MS),
            )

        val outcome = outcomeOf(pendingOutcomeEvents(events, shownAt + 12 * MINUTE_MS, config).single())

        assertTrue(outcome.leftApp)
        assertEquals(true, outcome.reopened)
        assertEquals(7 * MINUTE_MS, outcome.reopenedAfterMs)
    }

    @Test
    fun `coming back after the window is not a reopen`() {
        val events =
            listOf(
                shown(),
                background(shownAt + 3 * MINUTE_MS, 28 * MINUTE_MS),
                foreground(shownAt + 11 * MINUTE_MS),
            )

        val outcome = outcomeOf(pendingOutcomeEvents(events, shownAt + 12 * MINUTE_MS, config).single())

        assertEquals(false, outcome.reopened)
    }

    @Test
    fun `an activity switch within the app is not leaving it`() {
        val events =
            listOf(
                shown(),
                background(shownAt + 2 * MINUTE_MS, 27 * MINUTE_MS),
                foreground(shownAt + 2 * MINUTE_MS + 1_000),
            )

        val outcome = outcomeOf(pendingOutcomeEvents(events, shownAt + 12 * MINUTE_MS, config).single())

        assertFalse(outcome.leftApp)
        assertNull(outcome.leftAfterMs)
    }

    @Test
    fun `outcome is recorded once`() {
        val first = pendingOutcomeEvents(listOf(shown()), shownAt + 12 * MINUTE_MS, config)

        assertEquals(
            emptyList<EventEntity>(),
            pendingOutcomeEvents(listOf(shown()) + first, shownAt + 20 * MINUTE_MS, config),
        )
    }

    @Test
    fun `recorded outcomes are read back by nudge`() {
        val events =
            listOf(shown("n1"), shown("n2"), shown("n3"), background(shownAt + 3 * MINUTE_MS, 28 * MINUTE_MS))
        val outcomes = pendingOutcomeEvents(events.take(2) + events.last(), shownAt + 12 * MINUTE_MS, config)

        assertEquals(mapOf("n1" to true, "n2" to true), nudgeOutcomes(events + outcomes))
    }
}
