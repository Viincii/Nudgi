package com.vincentmignot.nudgi.core.nudge

/** The highest-priority rule that fires for [context] and has not been handled yet, if any. */
fun nudgeCandidate(
    context: NudgeContext,
    config: NudgeConfig,
): NudgeCandidate? =
    lateNightCandidate(context, config)
        ?: snoozeFollowUpCandidate(context, config)
        ?: longSessionCandidate(context, config)
        ?: dailyBudgetCandidate(context, config)

/**
 * Applies the guardrails to [nudgeCandidate]'s result. [holdoutDraw] is a uniform draw in `[0, 1)`;
 * it is only compared when a nudge would otherwise be shown, so the held-out nudges form a clean
 * control group.
 */
fun decideNudge(
    context: NudgeContext,
    config: NudgeConfig,
    holdoutDraw: Double,
): NudgeDecision {
    val candidate = nudgeCandidate(context, config) ?: return NudgeDecision.None
    if (context.nudgesShownToday >= config.dailyCap) {
        return NudgeDecision.Suppress(candidate, SuppressionReason.DailyCap)
    }
    // The user explicitly asked to be reminded, so neither the cooldown nor the holdout applies.
    if (candidate.rule == NudgeRule.SnoozeFollowUp) return NudgeDecision.Show(candidate)

    val lastShownAt = context.lastShownAt
    if (lastShownAt != null && context.now - lastShownAt < config.cooldownMs) {
        return NudgeDecision.Suppress(candidate, SuppressionReason.Cooldown)
    }
    if (holdoutDraw < config.holdoutProbability) {
        return NudgeDecision.Suppress(candidate, SuppressionReason.Holdout)
    }
    return NudgeDecision.Show(candidate)
}

/** Once per night, across all watched apps. */
private fun lateNightCandidate(
    context: NudgeContext,
    config: NudgeConfig,
): NudgeCandidate? {
    val nightStartedAt = context.nightStartedAt ?: return null
    if (context.lateNightUsageMs < config.lateNightThresholdMs) return null
    if (context.pastNudges.any { it.rule == NudgeRule.LateNight && it.timestamp >= nightStartedAt }) return null
    return NudgeCandidate(NudgeRule.LateNight, level = 1, thresholdMs = config.lateNightThresholdMs)
}

/** Comes back [NudgeConfig.snoozeMs] after a snooze, if the user is still in the same session. */
private fun snoozeFollowUpCandidate(
    context: NudgeContext,
    config: NudgeConfig,
): NudgeCandidate? {
    val snoozed =
        context.pastNudges
            .filter { it.packageName == context.packageName && it.response == NudgeResponse.Snooze }
            .maxByOrNull { it.respondedAt ?: it.timestamp } ?: return null
    val snoozedAt = snoozed.respondedAt ?: return null
    if (context.sessionStartedAt > snoozedAt) return null
    if (context.now < snoozedAt + config.snoozeMs) return null
    if (context.pastNudges.any { it.packageName == context.packageName && it.timestamp > snoozedAt }) return null
    return NudgeCandidate(
        NudgeRule.SnoozeFollowUp,
        level = snoozed.level,
        thresholdMs = config.snoozeMs,
        followUpOf = snoozed.nudgeId,
    )
}

/** Level 1 at [NudgeConfig.longSessionFirstMs], then one more level every repeat interval. */
private fun longSessionCandidate(
    context: NudgeContext,
    config: NudgeConfig,
): NudgeCandidate? {
    val sessionMs = context.sessionMs
    if (sessionMs < config.longSessionFirstMs) return null
    val level = ((sessionMs - config.longSessionFirstMs) / config.longSessionRepeatMs).toInt() + 1
    val handled =
        context.pastNudges.any {
            it.rule == NudgeRule.LongSession &&
                it.packageName == context.packageName &&
                it.timestamp >= context.sessionStartedAt &&
                it.level >= level
        }
    if (handled) return null
    return NudgeCandidate(
        NudgeRule.LongSession,
        level = level,
        thresholdMs = config.longSessionFirstMs + (level - 1) * config.longSessionRepeatMs,
    )
}

/** One level per threshold crossed today; a skipped level is not caught up. */
private fun dailyBudgetCandidate(
    context: NudgeContext,
    config: NudgeConfig,
): NudgeCandidate? {
    val level = config.dailyBudgetThresholdsMs.count { context.dailyUsageMs >= it }
    if (level == 0) return null
    val handled =
        context.pastNudges.any {
            it.rule == NudgeRule.DailyBudget &&
                it.packageName == context.packageName &&
                it.timestamp >= context.dayStartedAt &&
                it.level >= level
        }
    if (handled) return null
    return NudgeCandidate(NudgeRule.DailyBudget, level = level, thresholdMs = config.dailyBudgetThresholdsMs[level - 1])
}
