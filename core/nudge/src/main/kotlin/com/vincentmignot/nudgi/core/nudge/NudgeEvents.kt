package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_FOREGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_OUTCOME
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_RESPONSE
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SUPPRESSED
import com.vincentmignot.nudgi.core.database.EventEntity

/** What a decision records of [this] context, and all the shadow bandit sees of it. */
fun NudgeContext.snapshot(): NudgeContextSnapshot =
    NudgeContextSnapshot(
        sessionMs = sessionMs,
        dailyMs = dailyUsageMs,
        lateNightMs = lateNightUsageMs,
        localHour = localHour,
        weekday = weekday,
        nudgesToday = nudgesShownToday,
        msSinceLastNudge = lastShownAt?.let { now - it },
        snoozesToday =
            pastNudges.count {
                it.packageName == packageName &&
                    it.response == NudgeResponse.Snooze &&
                    (it.respondedAt ?: 0L) in dayStartedAt..now
            },
        frictionLevelReached = frictionState(this).level.value,
    )

/** The `nudge_shown` or `nudge_suppressed` row for [decision], which must not be [NudgeDecision.None]. */
fun decisionEvent(
    decision: NudgeDecision,
    friction: FrictionDecision,
    context: NudgeContext,
    config: NudgeConfig,
    nudgeId: String,
    shadow: ShadowMetadata? = null,
    expression: CoachExpression? = null,
): EventEntity {
    val (eventType, candidate, reason) =
        when (decision) {
            is NudgeDecision.Show -> Triple(EVENT_TYPE_NUDGE_SHOWN, decision.candidate, null)
            is NudgeDecision.Suppress -> Triple(EVENT_TYPE_NUDGE_SUPPRESSED, decision.candidate, decision.reason)
            NudgeDecision.None -> error("No event is recorded when no rule fires")
        }
    val metadata =
        NudgeDecisionMetadata(
            nudgeId = nudgeId,
            policyId = RULES_POLICY_ID,
            ruleId = candidate.rule.id,
            level = candidate.level,
            thresholdMs = candidate.thresholdMs,
            followUpOf = candidate.followUpOf,
            reason = reason?.id,
            holdoutProbability = config.holdoutProbability,
            context = context.snapshot(),
            shadow = shadow,
            frictionLevel = friction.applied.value,
            requestedFrictionLevel = friction.requested.value,
            escalationProbability = config.escalationProbability,
            frictionPaused = friction.paused,
            frictionFallback = friction.fallback?.id,
            expression = expression?.id,
            expressionProbability = expression?.let { expressionProbability(config) },
        )
    return EventEntity(
        timestamp = context.now,
        eventType = eventType,
        packageName = context.packageName,
        durationMs = 0,
        metadata = NudgeJson.encodeToString(metadata),
    )
}

fun responseEvent(
    nudgeId: String,
    packageName: String,
    response: NudgeResponse,
    now: Long,
): EventEntity =
    EventEntity(
        timestamp = now,
        eventType = EVENT_TYPE_NUDGE_RESPONSE,
        packageName = packageName,
        durationMs = 0,
        metadata = NudgeJson.encodeToString(NudgeResponseMetadata(nudgeId, response.id)),
    )

/**
 * `nudge_outcome` rows for the shown and held-out nudges in [events] whose observation window has
 * closed and that have no outcome yet. Leaving the app is the candidate reward: it measures what
 * the nudge changed, where the notification buttons mostly measure politeness.
 */
fun pendingOutcomeEvents(
    events: List<EventEntity>,
    now: Long,
    config: NudgeConfig,
): List<EventEntity> {
    val recorded = nudgeOutcomes(events)
    return pastNudges(events)
        .filter { it.nudgeId !in recorded && now >= it.timestamp + config.outcomeWindowMs + config.sessionMergeGapMs }
        .map { nudge ->
            val leftAt = leftAppAt(events, nudge.packageName, nudge.timestamp, config)
            val reopenedAt = leftAt?.let { reopenedAppAt(events, nudge.packageName, it, nudge.timestamp, config) }
            EventEntity(
                timestamp = now,
                eventType = EVENT_TYPE_NUDGE_OUTCOME,
                packageName = nudge.packageName,
                durationMs = 0,
                metadata =
                    NudgeJson.encodeToString(
                        NudgeOutcomeMetadata(
                            nudgeId = nudge.nudgeId,
                            leftApp = leftAt != null,
                            leftAfterMs = leftAt?.let { it - nudge.timestamp },
                            windowMs = config.outcomeWindowMs,
                            reopened = reopenedAt != null,
                            reopenedAfterMs = reopenedAt?.let { it - nudge.timestamp },
                        ),
                    ),
            )
        }
}

/** Whether the user left the app after each nudge in [events] whose outcome is recorded, by `nudge_id`. */
fun nudgeOutcomes(events: List<EventEntity>): Map<String, Boolean> =
    events
        .filter { it.eventType == EVENT_TYPE_NUDGE_OUTCOME }
        .mapNotNull { decodeOrNull<NudgeOutcomeMetadata>(it.metadata) }
        .associate { it.nudgeId to it.leftApp }

/**
 * The first time [packageName] went to the background within the outcome window after [since]
 * without coming back within the merge gap, which would only be an activity switch.
 */
private fun leftAppAt(
    events: List<EventEntity>,
    packageName: String,
    since: Long,
    config: NudgeConfig,
): Long? {
    val appEvents = events.filter { it.packageName == packageName }
    return appEvents
        .filter {
            it.eventType == EVENT_TYPE_APP_BACKGROUND &&
                it.timestamp > since &&
                it.timestamp <= since + config.outcomeWindowMs
        }.firstOrNull { background ->
            appEvents.none {
                it.eventType == EVENT_TYPE_APP_FOREGROUND &&
                    it.timestamp > background.timestamp &&
                    it.timestamp <= background.timestamp + config.sessionMergeGapMs
            }
        }?.timestamp
}

/** The first time [packageName] came back to the foreground after [leftAt], within the outcome window of [since]. */
private fun reopenedAppAt(
    events: List<EventEntity>,
    packageName: String,
    leftAt: Long,
    since: Long,
    config: NudgeConfig,
): Long? =
    events
        .firstOrNull {
            it.packageName == packageName &&
                it.eventType == EVENT_TYPE_APP_FOREGROUND &&
                it.timestamp > leftAt &&
                it.timestamp <= since + config.outcomeWindowMs
        }?.timestamp
