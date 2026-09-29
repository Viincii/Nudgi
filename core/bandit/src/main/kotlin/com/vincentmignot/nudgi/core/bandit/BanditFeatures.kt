package com.vincentmignot.nudgi.core.bandit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

private const val MINUTE_MS = 60_000.0
private const val SINCE_LAST_NUDGE_CAP_MINUTES = 120.0
private const val MAX_FRICTION_LEVEL = 3.0

/**
 * The rule ids of `core:nudge`, in a fixed order for the one-hot encoding. The bandit does not
 * depend on that module; an id missing from this list encodes as all zeros.
 */
private val RULE_IDS = listOf("late_night", "snooze_followup", "long_session", "daily_budget")

/** What the bandit knows about a decision; everything here is recorded with it. */
data class BanditContext(
    val localHour: Int,
    /** ISO day of week, 1 for Monday to 7 for Sunday. */
    val weekday: Int,
    val sessionMs: Long,
    val dailyMs: Long,
    val lateNightMs: Long,
    val nudgesToday: Int,
    val msSinceLastNudge: Long?,
    val snoozesToday: Int,
    val frictionLevelReached: Int,
    val ruleId: String,
)

/**
 * The feature vector of [context]. Durations and counts are on a log scale, so the first minutes
 * weigh more than the hundredth; the hour is on a circle, so 23:00 is next to 00:00. The first
 * feature is a constant, which carries each action's prior mean.
 */
fun features(context: BanditContext): DoubleArray {
    val hourAngle = 2 * PI * context.localHour / 24
    val sinceLastMinutes =
        context.msSinceLastNudge?.let { minOf(it / MINUTE_MS, SINCE_LAST_NUDGE_CAP_MINUTES) }
            ?: SINCE_LAST_NUDGE_CAP_MINUTES
    val ruleIndex = RULE_IDS.indexOf(context.ruleId)
    return doubleArrayOf(
        1.0,
        sin(hourAngle),
        cos(hourAngle),
        if (context.weekday >= 6) 1.0 else 0.0,
        if (RewardV1.isNight(context.localHour)) 1.0 else 0.0,
        ln(1 + context.sessionMs / MINUTE_MS),
        ln(1 + context.dailyMs / MINUTE_MS),
        ln(1 + context.lateNightMs / MINUTE_MS),
        ln(1.0 + context.nudgesToday),
        sinceLastMinutes / SINCE_LAST_NUDGE_CAP_MINUTES,
        ln(1.0 + context.snoozesToday),
        context.frictionLevelReached / MAX_FRICTION_LEVEL,
    ) + DoubleArray(RULE_IDS.size) { if (it == ruleIndex) 1.0 else 0.0 }
}

val FEATURE_COUNT: Int = 12 + RULE_IDS.size
