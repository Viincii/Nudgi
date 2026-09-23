package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_FOREGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_OUTCOME
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_RESPONSE
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SUPPRESSED
import com.vincentmignot.nudgi.core.database.EventEntity

/** The `nudge_shown` or `nudge_suppressed` row for [decision], which must not be [NudgeDecision.None]. */
fun decisionEvent(
    decision: NudgeDecision,
    context: NudgeContext,
    config: NudgeConfig,
    nudgeId: String,
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
            ruleId = candidate.rule.id,
            level = candidate.level,
            thresholdMs = candidate.thresholdMs,
            followUpOf = candidate.followUpOf,
            reason = reason?.id,
            holdoutProbability = config.holdoutProbability,
            context =
                NudgeContextSnapshot(
                    sessionMs = context.sessionMs,
                    dailyMs = context.dailyUsageMs,
                    lateNightMs = context.lateNightUsageMs,
                    localHour = context.localHour,
                    weekday = context.weekday,
                    nudgesToday = context.nudgesShownToday,
                    msSinceLastNudge = context.lastShownAt?.let { context.now - it },
                ),
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
    val recorded =
        events
            .filter { it.eventType == EVENT_TYPE_NUDGE_OUTCOME }
            .mapNotNull { decodeOrNull<NudgeOutcomeMetadata>(it.metadata)?.nudgeId }
            .toSet()
    return pastNudges(events)
        .filter { it.nudgeId !in recorded && now >= it.timestamp + config.outcomeWindowMs + config.sessionMergeGapMs }
        .map { nudge ->
            val leftAt = leftAppAt(events, nudge.packageName, nudge.timestamp, config)
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
                        ),
                    ),
            )
        }
}

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
