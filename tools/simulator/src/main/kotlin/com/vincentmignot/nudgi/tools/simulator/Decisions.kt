package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.BanditContext
import com.vincentmignot.nudgi.core.bandit.RewardV1
import kotlin.random.Random

private const val MINUTE_MS = 60_000L
private const val SNOOZE_FOLLOW_UP = "snooze_followup"
private const val MAX_FRICTION_LEVEL = 3

/** The friction levels of `core:nudge`, 0 to 3, in the order of the actions after "nothing". */
fun frictionAction(level: Int): BanditAction = BanditAction.entries[level + 1]

fun frictionLevel(action: BanditAction): Int? = (action.ordinal - 1).takeIf { it >= 0 }

/** A moment where a rule fired: what the bandit sees, plus the level the rules' ladder asks for. */
data class SimDecision(
    val context: BanditContext,
    /** Friction level the rules request; above [BanditContext.frictionLevelReached] only on an escalation. */
    val requestedLevel: Int,
) {
    val isFollowUp: Boolean get() = context.ruleId == SNOOZE_FOLLOW_UP

    /** As in the app: after a snooze the user asked to be reminded, so doing nothing is not an option. */
    val allowed: Set<BanditAction>
        get() = if (isFollowUp) BanditAction.entries.toSet() - BanditAction.Nothing else BanditAction.entries.toSet()
}

/** Where simulated decisions come from. */
fun interface DecisionSource {
    fun next(random: Random): SimDecision
}

/** Real decisions of an export, drawn with replacement: the user's own mix of hours, sessions and rules. */
class ResampledDecisions(
    private val decisions: List<SimDecision>,
) : DecisionSource {
    init {
        require(decisions.isNotEmpty()) { "No decision to resample" }
    }

    override fun next(random: Random): SimDecision = decisions.random(random)
}

/** Invented decisions with roughly the real shape, for running without an export and for tests. */
object SyntheticDecisions : DecisionSource {
    // Mostly evenings and nights, as on the real device.
    private val hours = listOf(13, 18, 20, 21, 22, 23, 0, 0, 1, 2)
    private val rules = listOf("late_night", "long_session", "long_session", "daily_budget", SNOOZE_FOLLOW_UP)

    override fun next(random: Random): SimDecision {
        val hour = hours.random(random)
        val rule = rules.random(random)
        val reached = random.nextInt(0, MAX_FRICTION_LEVEL + 1)
        val escalates = rule == SNOOZE_FOLLOW_UP && reached < MAX_FRICTION_LEVEL && random.nextDouble() < 0.3
        val context =
            BanditContext(
                localHour = hour,
                weekday = random.nextInt(1, 8),
                sessionMs = random.nextLong(5, 60) * MINUTE_MS,
                dailyMs = random.nextLong(30, 240) * MINUTE_MS,
                lateNightMs = if (RewardV1.isNight(hour)) random.nextLong(0, 60) * MINUTE_MS else 0,
                nudgesToday = random.nextInt(0, 8),
                msSinceLastNudge = random.nextLong(5, 180) * MINUTE_MS,
                snoozesToday = random.nextInt(0, 3),
                frictionLevelReached = reached,
                ruleId = rule,
            )
        return SimDecision(context, requestedLevel = if (escalates) reached + 1 else reached)
    }
}
