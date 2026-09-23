package com.vincentmignot.nudgi.core.nudge

/** Declaration order is priority order: when several rules fire at once, the first one wins. */
enum class NudgeRule(
    val id: String,
) {
    LateNight("late_night"),
    SnoozeFollowUp("snooze_followup"),
    LongSession("long_session"),
    DailyBudget("daily_budget"),
    ;

    companion object {
        fun fromId(id: String?): NudgeRule? = entries.firstOrNull { it.id == id }
    }
}

enum class NudgeResponse(
    val id: String,
) {
    Stop("stop"),
    Snooze("snooze"),
    Dismissed("dismissed"),
    ;

    companion object {
        fun fromId(id: String?): NudgeResponse? = entries.firstOrNull { it.id == id }
    }
}

enum class SuppressionReason(
    val id: String,
) {
    Cooldown("cooldown"),
    DailyCap("daily_cap"),

    /** Randomly withheld as a control group; the only reason that is not the rules' doing. */
    Holdout("holdout"),
    NotificationsDisabled("notifications_disabled"),
    ;

    companion object {
        fun fromId(id: String?): SuppressionReason? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A nudge that was decided: either [shown], or held out as a control. Both count as handled, so a
 * rule does not fire again for the same level, and both get an outcome.
 */
data class PastNudge(
    val nudgeId: String,
    val timestamp: Long,
    val packageName: String,
    val rule: NudgeRule,
    val level: Int,
    val shown: Boolean,
    val response: NudgeResponse? = null,
    val respondedAt: Long? = null,
)

/** What the rules look at: the watched app currently in the foreground and the recent history. */
data class NudgeContext(
    val now: Long,
    val packageName: String,
    val sessionStartedAt: Long,
    val dayStartedAt: Long,
    /** Start of the current late-night window, or null outside of it. */
    val nightStartedAt: Long?,
    /** Today's usage of [packageName], including the running session. */
    val dailyUsageMs: Long,
    /** Usage of all watched apps since [nightStartedAt]. */
    val lateNightUsageMs: Long,
    val localHour: Int,
    /** ISO day of week, 1 for Monday to 7 for Sunday. */
    val weekday: Int,
    val pastNudges: List<PastNudge>,
) {
    val sessionMs: Long get() = now - sessionStartedAt

    val nudgesShownToday: Int get() = pastNudges.count { it.shown && it.timestamp >= dayStartedAt }

    val lastShownAt: Long? get() = pastNudges.filter { it.shown }.maxOfOrNull { it.timestamp }
}

data class NudgeCandidate(
    val rule: NudgeRule,
    val level: Int,
    val thresholdMs: Long,
    /** For a snooze follow-up, the nudge that was snoozed. */
    val followUpOf: String? = null,
)

sealed interface NudgeDecision {
    data object None : NudgeDecision

    data class Show(
        val candidate: NudgeCandidate,
    ) : NudgeDecision

    data class Suppress(
        val candidate: NudgeCandidate,
        val reason: SuppressionReason,
    ) : NudgeDecision
}
