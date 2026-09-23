package com.vincentmignot.nudgi.core.nudge

private const val MINUTE_MS = 60_000L

/**
 * Every threshold of the rule-based nudges in one place, so they can be tuned together and later
 * compared against what the on-device model learns.
 *
 * The late-night window is assumed to cross midnight: [lateNightStartHour] is after
 * [lateNightEndHour].
 */
data class NudgeConfig(
    val longSessionFirstMs: Long = 20 * MINUTE_MS,
    val longSessionRepeatMs: Long = 15 * MINUTE_MS,
    val dailyBudgetThresholdsMs: List<Long> = listOf(60L, 90L, 120L).map { it * MINUTE_MS },
    val lateNightStartHour: Int = 23,
    val lateNightEndHour: Int = 6,
    val lateNightThresholdMs: Long = 10 * MINUTE_MS,
    val snoozeMs: Long = 5 * MINUTE_MS,
    val cooldownMs: Long = 10 * MINUTE_MS,
    val dailyCap: Int = 8,
    val holdoutProbability: Double = 0.1,
    val outcomeWindowMs: Long = 10 * MINUTE_MS,
    /** Foreground pieces of one app separated by less than this count as one session. */
    val sessionMergeGapMs: Long = MINUTE_MS,
)
