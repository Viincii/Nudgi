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
    /**
     * High on purpose while Nudgi collects data for the model: half of the nudges that would be
     * shown form the control group, so the effect of a nudge can be measured within weeks.
     */
    val holdoutProbability: Double = 0.5,
    val outcomeWindowMs: Long = 10 * MINUTE_MS,
    /** Foreground pieces of one app separated by less than this count as one session. */
    val sessionMergeGapMs: Long = MINUTE_MS,
    /** Snoozes taken at a friction level before the next snooze follow-up asks for the level above. */
    val snoozesPerFrictionLevel: Int = 2,
    /** How long the "5 more minutes" button stays locked on a countdown overlay. */
    val frictionCountdownMs: Long = 10_000L,
    /** Probability that a higher friction level asked for by the rules is actually applied. */
    val escalationProbability: Double = 0.8,
    val frictionPauseMs: Long = 60 * MINUTE_MS,
    /** Nudgi's expressions an intervention is drawn among, uniformly (decision 0021). */
    val coachExpressions: List<CoachExpression> = CoachExpression.entries,
)
