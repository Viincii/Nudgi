package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.LinearThompsonSampling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class SimulatorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val aftermath = Aftermath()
    private val user = Scenario.CalibratedHigh.user(Calibration.DEFAULT, aftermath)

    @Test
    fun `sampled benefits average to the expected benefit`() {
        val random = Random(1)
        repeat(20) {
            val decision = SyntheticDecisions.next(random)
            val action = decision.allowed.random(random)
            val sampled =
                List(20_000) { user.benefit(decision.context, user.sample(decision.context, action, random)) }.average()

            assertEquals("${decision.context} $action", user.expectedBenefit(decision.context, action), sampled, 0.01)
        }
    }

    @Test
    fun `the rules hold out half the nudges and escalate four times out of five`() {
        val rules = RulesPolicy()
        val base = SyntheticDecisions.next(Random(1)).context
        val escalation =
            SimDecision(base.copy(ruleId = "snooze_followup", frictionLevelReached = 1), requestedLevel = 2)
        val plain = SimDecision(base.copy(ruleId = "long_session", frictionLevelReached = 0), requestedLevel = 0)

        assertEquals(
            mapOf(BanditAction.CountdownOverlay to 0.8, BanditAction.Overlay to 0.2),
            rules.distribution(escalation).mapValues { Math.round(it.value * 100) / 100.0 },
        )
        assertEquals(
            mapOf(BanditAction.Nothing to 0.5, BanditAction.Notification to 0.5),
            rules.distribution(plain),
        )
    }

    @Test
    fun `the exploration of rules_v5 moves a level up or down and sometimes closes the app`() {
        val base = SyntheticDecisions.next(Random(1)).context
        val overlay = SimDecision(base.copy(ruleId = "long_session", frictionLevelReached = 1), requestedLevel = 1)

        val distribution = RulesPolicy.V5.distribution(overlay).mapValues { Math.round(it.value * 1000) / 1000.0 }

        // Shown 80%: the overlay 65% of that, a neighbour 15% each, a forced close 5%.
        assertEquals(
            mapOf(
                BanditAction.Nothing to 0.2,
                BanditAction.Notification to 0.12,
                BanditAction.Overlay to 0.52,
                BanditAction.CountdownOverlay to 0.12,
                BanditAction.ForcedClose to 0.04,
            ),
            distribution,
        )
    }

    @Test
    fun `every action has a chance under rules_v5 whatever the level reached`() {
        val random = Random(4)
        repeat(200) {
            val decision = SyntheticDecisions.next(random)
            val distribution = RulesPolicy.V5.distribution(decision)

            assertEquals(1.0, distribution.values.sum(), 1e-9)
            assertTrue(BanditAction.ForcedClose in distribution)
            assertEquals(decision.isFollowUp, BanditAction.Nothing !in distribution)
        }
    }

    @Test
    fun `the calibration recovers the stop probabilities it was simulated with`() {
        val random = Random(2)
        val truth = Scenario.CalibratedLow.user(Calibration.DEFAULT, aftermath)
        val world = World(truth, SyntheticDecisions)
        val decisions =
            List(20_000) {
                val decision = world.source.next(random)
                val action = world.rules.sample(decision, random)
                val benefit = truth.benefit(decision.context, truth.sample(decision.context, action, random))
                ExportedDecision(timestamp = 0, decision, action, benefit)
            }

        val fitted = Calibration.fit(decisions, aftermath)

        assertEquals(Calibration.DEFAULT.nothingDay.stopProbability, fitted.nothingDay.stopProbability, 0.03)
        assertEquals(Calibration.DEFAULT.nothingNight.stopProbability, fitted.nothingNight.stopProbability, 0.03)
        assertEquals(Calibration.DEFAULT.notificationDay.stopProbability, fitted.notificationDay.stopProbability, 0.03)
        assertEquals(
            Calibration.DEFAULT.notificationNight.stopProbability,
            fitted.notificationNight.stopProbability,
            0.03,
        )
    }

    @Test
    fun `a synthetic export reads back with the simulated actions and benefits`() {
        val world = World(user, SyntheticDecisions)
        val logged = runShadow(world, LinearThompsonSampling(propensityDraws = 1), 200, Random(3))
        val file = folder.newFile("export.zip")

        writeSyntheticExport(file, logged, holdoutProbability = 0.5, escalationProbability = 0.8)
        val read = readExport(file, excludedIds = emptySet())

        assertEquals(logged.map { it.rulesAction }, read.map { it.action })
        assertEquals(logged.map { it.decision }, read.map { it.decision })
        logged.zip(read).forEach { (written, back) -> assertEquals(written.benefit, back.benefit!!, 1e-9) }
    }
}
