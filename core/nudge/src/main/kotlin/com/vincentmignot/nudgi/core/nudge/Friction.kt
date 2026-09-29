package com.vincentmignot.nudgi.core.nudge

/** Declaration order is escalation order. [value] is what `events` records. */
enum class FrictionLevel(
    val value: Int,
) {
    Notification(0),
    Overlay(1),

    /** The overlay, with "5 more minutes" locked for [NudgeConfig.frictionCountdownMs]. */
    CountdownOverlay(2),

    /** Sends the user to the home screen. */
    ForcedClose(3),
    ;

    companion object {
        fun fromValue(value: Int?): FrictionLevel? = entries.firstOrNull { it.value == value }
    }
}

/** Why a nudge got a notification instead of the friction it was decided with. */
enum class FrictionFallback(
    val id: String,
) {
    /** The accessibility service, which draws overlays and sends the user home, was not running. */
    ServiceUnavailable("service_unavailable"),
}

/** The level [packageName][NudgeContext.packageName] has reached today, and the snoozes taken at it. */
data class FrictionState(
    val level: FrictionLevel,
    val snoozesAtLevel: Int,
)

data class FrictionDecision(
    /** What the ladder asks for. */
    val requested: FrictionLevel,
    /** What the user gets: lower than [requested] when escalation is held out, paused or unavailable. */
    val applied: FrictionLevel,
    val paused: Boolean,
    val fallback: FrictionFallback? = null,
) {
    fun fallingBack(reason: FrictionFallback): FrictionDecision =
        copy(applied = FrictionLevel.Notification, fallback = reason)
}

/**
 * The ladder as the user experienced it today in the foreground app: the highest level applied to a
 * shown nudge. A forced close is one-off: the app then starts again from a countdown overlay, with
 * its snoozes counted afresh. Held-out and paused nudges applied less, so they never raise it.
 */
fun frictionState(context: NudgeContext): FrictionState {
    val today =
        context.pastNudges
            .filter { it.shown && it.packageName == context.packageName && it.timestamp >= context.dayStartedAt }
            .sortedBy { it.timestamp }
    val lastClose = today.indexOfLast { it.frictionLevel == FrictionLevel.ForcedClose }
    val sinceClose = today.drop(lastClose + 1)
    val floor = if (lastClose >= 0) FrictionLevel.CountdownOverlay else FrictionLevel.Notification
    val level = maxOf(floor, sinceClose.maxOfOrNull { it.frictionLevel } ?: floor)
    val snoozes = sinceClose.count { it.frictionLevel == level && it.response == NudgeResponse.Snooze }
    return FrictionState(level, snoozes)
}

/**
 * The friction for a nudge on [candidate]. The level only rises on a snooze follow-up, once the
 * user has snoozed [NudgeConfig.snoozesPerFrictionLevel] times at the current level; every other
 * rule fires at the level already reached.
 *
 * A rise is applied with [NudgeConfig.escalationProbability] only, [escalationDraw] being a uniform
 * draw in `[0, 1)`, so every level keeps a comparable control group. A pause taken in Settings
 * caps the applied level to a notification without touching the ladder.
 */
fun decideFriction(
    context: NudgeContext,
    candidate: NudgeCandidate,
    config: NudgeConfig,
    escalationDraw: Double,
): FrictionDecision {
    val state = frictionState(context)
    val escalates =
        candidate.rule == NudgeRule.SnoozeFollowUp && state.snoozesAtLevel >= config.snoozesPerFrictionLevel
    val requested =
        if (escalates) {
            FrictionLevel.entries.getOrElse(
                state.level.ordinal + 1,
            ) { state.level }
        } else {
            state.level
        }
    val escalated =
        if (requested > state.level &&
            escalationDraw >= config.escalationProbability
        ) {
            state.level
        } else {
            requested
        }
    val paused = context.frictionPausedUntil?.let { context.now < it } == true
    return FrictionDecision(
        requested = requested,
        applied = if (paused) FrictionLevel.Notification else escalated,
        paused = paused,
    )
}
