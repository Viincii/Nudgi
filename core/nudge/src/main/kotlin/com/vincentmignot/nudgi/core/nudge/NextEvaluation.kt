package com.vincentmignot.nudgi.core.nudge

import java.time.Instant
import java.time.ZoneId

/**
 * Floor on how soon the next evaluation is scheduled. A rule that fires now but cannot be shown
 * (notifications turned off) would otherwise ask to be evaluated again immediately, forever.
 */
private const val MIN_DELAY_MS = 60_000L

/**
 * When the rules should next be evaluated if the user stays in [NudgeContext.packageName]: the
 * earliest instant at which a rule not handled yet would fire, pushed back to the end of the
 * cooldown. Null once the daily cap is reached. The result is only a wake-up time: whatever
 * happened in between, the evaluation at that instant decides again from the events.
 */
fun nextEvaluationAt(
    context: NudgeContext,
    config: NudgeConfig,
    zone: ZoneId,
): Long? {
    if (context.nudgesShownToday >= config.dailyCap) return null

    val snoozeFollowUpAt = snoozeFollowUpAt(context, config)
    val otherRulesAt =
        listOfNotNull(
            longSessionAt(context, config),
            dailyBudgetAt(context, config),
            lateNightAt(context, config, zone),
        ).minOrNull()?.let { at ->
            val cooldownEndsAt = context.lastShownAt?.plus(config.cooldownMs)
            if (cooldownEndsAt != null) maxOf(at, cooldownEndsAt) else at
        }

    val next = listOfNotNull(snoozeFollowUpAt, otherRulesAt).minOrNull() ?: return null
    return maxOf(next, context.now + MIN_DELAY_MS)
}

private fun snoozeFollowUpAt(
    context: NudgeContext,
    config: NudgeConfig,
): Long? {
    val snoozed =
        context.pastNudges
            .filter { it.packageName == context.packageName && it.response == NudgeResponse.Snooze }
            .maxByOrNull { it.respondedAt ?: it.timestamp } ?: return null
    val snoozedAt = snoozed.respondedAt ?: return null
    if (context.sessionStartedAt > snoozedAt) return null
    if (context.pastNudges.any { it.packageName == context.packageName && it.timestamp > snoozedAt }) return null
    return snoozedAt + config.snoozeMs
}

private fun longSessionAt(
    context: NudgeContext,
    config: NudgeConfig,
): Long {
    val handledLevel =
        context.pastNudges
            .filter {
                it.rule == NudgeRule.LongSession &&
                    it.packageName == context.packageName &&
                    it.timestamp >= context.sessionStartedAt
            }.maxOfOrNull { it.level } ?: 0
    return context.sessionStartedAt + config.longSessionFirstMs + handledLevel * config.longSessionRepeatMs
}

private fun dailyBudgetAt(
    context: NudgeContext,
    config: NudgeConfig,
): Long? {
    val handledLevel =
        context.pastNudges
            .filter {
                it.rule == NudgeRule.DailyBudget &&
                    it.packageName == context.packageName &&
                    it.timestamp >= context.dayStartedAt
            }.maxOfOrNull { it.level } ?: 0
    val threshold = config.dailyBudgetThresholdsMs.getOrNull(handledLevel) ?: return null
    return context.now + (threshold - context.dailyUsageMs).coerceAtLeast(0L)
}

private fun lateNightAt(
    context: NudgeContext,
    config: NudgeConfig,
    zone: ZoneId,
): Long? {
    val nightStartedAt = context.nightStartedAt
    if (nightStartedAt != null) {
        if (context.pastNudges.any { it.rule == NudgeRule.LateNight && it.timestamp >= nightStartedAt }) return null
        return context.now + (config.lateNightThresholdMs - context.lateNightUsageMs).coerceAtLeast(0L)
    }
    // Before tonight's window: assume the user keeps scrolling past its start.
    val tonight =
        Instant
            .ofEpochMilli(context.now)
            .atZone(zone)
            .toLocalDate()
            .atTime(config.lateNightStartHour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    return tonight + config.lateNightThresholdMs
}
