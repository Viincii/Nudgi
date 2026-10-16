package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.bandit.BanditAction
import org.junit.Assert.assertEquals
import org.junit.Test

class RulesActionProbabilitiesTest {
    private val config = NudgeConfig()
    private val longSession = NudgeCandidate(NudgeRule.LongSession, level = 1, thresholdMs = 20 * MINUTE_MS)
    private val followUp = NudgeCandidate(NudgeRule.SnoozeFollowUp, level = 1, thresholdMs = 5 * MINUTE_MS)

    private fun friction(
        reached: FrictionLevel,
        requested: FrictionLevel = reached,
        paused: Boolean = false,
    ) = FrictionDecision(requested = requested, applied = reached, paused = paused, reached = reached)

    private fun assertProbabilities(
        expected: Map<BanditAction, Double>,
        actual: Map<BanditAction, Double>,
    ) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (action, probability) ->
            assertEquals(action.id, probability, actual.getValue(action), 1e-9)
        }
    }

    @Test
    fun `a nudge at a notification is held out, shown, moved up or closed`() {
        val probabilities =
            rulesActionProbabilities(NudgeDecision.Show(longSession), friction(FrictionLevel.Notification), config)

        assertProbabilities(
            mapOf(
                BanditAction.Nothing to 0.2,
                BanditAction.Notification to 0.8 * 0.65,
                BanditAction.Overlay to 0.8 * 0.3,
                BanditAction.ForcedClose to 0.8 * 0.05,
            ),
            probabilities,
        )
    }

    @Test
    fun `a held-out decision has the same probabilities as a shown one`() {
        val held = NudgeDecision.Suppress(longSession, SuppressionReason.Holdout)
        val overlay = friction(FrictionLevel.Overlay)

        assertEquals(
            rulesActionProbabilities(NudgeDecision.Show(longSession), overlay, config),
            rulesActionProbabilities(held, overlay, config),
        )
    }

    @Test
    fun `an escalating follow-up is never held out and explores both ladder levels`() {
        val probabilities =
            rulesActionProbabilities(
                NudgeDecision.Show(followUp),
                friction(FrictionLevel.Notification, requested = FrictionLevel.Overlay),
                config,
            )

        // Escalated to the overlay 0.8, kept at the notification 0.2, each then explored.
        assertProbabilities(
            mapOf(
                BanditAction.Notification to 0.8 * 0.15 + 0.2 * 0.65,
                BanditAction.Overlay to 0.8 * 0.65 + 0.2 * 0.3,
                BanditAction.CountdownOverlay to 0.8 * 0.15,
                BanditAction.ForcedClose to 0.05,
            ),
            probabilities,
        )
    }

    @Test
    fun `a forced close reached only moves down`() {
        val probabilities =
            rulesActionProbabilities(NudgeDecision.Show(followUp), friction(FrictionLevel.ForcedClose), config)

        assertProbabilities(
            mapOf(BanditAction.CountdownOverlay to 0.3, BanditAction.ForcedClose to 0.7),
            probabilities,
        )
    }

    @Test
    fun `a pause shows every nudge as a notification`() {
        val probabilities =
            rulesActionProbabilities(
                NudgeDecision.Show(longSession),
                friction(FrictionLevel.CountdownOverlay, paused = true),
                config,
            )

        assertProbabilities(mapOf(BanditAction.Nothing to 0.2, BanditAction.Notification to 0.8), probabilities)
    }

    @Test
    fun `a cooldown always does nothing`() {
        val cooldown = NudgeDecision.Suppress(longSession, SuppressionReason.Cooldown)

        assertEquals(
            mapOf(BanditAction.Nothing to 1.0),
            rulesActionProbabilities(cooldown, friction(FrictionLevel.Overlay), config),
        )
    }

    @Test
    fun `the probabilities always sum to one`() {
        for (reached in FrictionLevel.entries) {
            for (requested in FrictionLevel.entries.filter { it >= reached }) {
                for (candidate in listOf(longSession, followUp)) {
                    for (paused in listOf(false, true)) {
                        val probabilities =
                            rulesActionProbabilities(
                                NudgeDecision.Show(candidate),
                                friction(reached, requested, paused),
                                config,
                            )
                        assertEquals("$reached $requested $candidate $paused", 1.0, probabilities.values.sum(), 1e-9)
                    }
                }
            }
        }
    }
}
