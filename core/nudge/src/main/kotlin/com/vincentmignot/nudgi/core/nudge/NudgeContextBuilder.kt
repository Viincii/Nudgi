package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_FOREGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_RESPONSE
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SUPPRESSED
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.usagestats.UsageSession
import com.vincentmignot.nudgi.core.usagestats.localDateOf
import com.vincentmignot.nudgi.core.usagestats.sessionsFrom
import com.vincentmignot.nudgi.core.usagestats.startMillis
import java.time.Instant
import java.time.ZoneId

/** The app in the foreground as of the last event, and since when. */
data class CurrentSession(
    val packageName: String,
    /** Start of the merged session, across short gaps such as switching activities. */
    val startedAt: Long,
    /** Start of the last foreground piece, still open. */
    val pieceStartedAt: Long,
)

/**
 * Builds the rules' view of the world from chronologically ordered [events], or returns null when
 * no watched app is in the foreground.
 */
fun buildNudgeContext(
    events: List<EventEntity>,
    now: Long,
    zone: ZoneId,
    isWatched: (String) -> Boolean,
    config: NudgeConfig,
): NudgeContext? {
    val current = currentSession(events, config.sessionMergeGapMs) ?: return null
    if (!isWatched(current.packageName)) return null

    val dayStartedAt = localDateOf(now, zone).startMillis(zone)
    val nightStartedAt = nightStartedAt(now, zone, config)
    val sessions =
        sessionsFrom(events.filter { it.eventType == EVENT_TYPE_APP_BACKGROUND }) +
            UsageSession(current.packageName, current.pieceStartedAt, now)

    val dailyUsageMs =
        sessions
            .filter { it.packageName == current.packageName }
            .sumOf { it.overlapMs(dayStartedAt, now) }
    val lateNightUsageMs =
        nightStartedAt?.let { start ->
            sessions.filter { isWatched(it.packageName) }.sumOf { it.overlapMs(start, now) }
        } ?: 0L
    val local = Instant.ofEpochMilli(now).atZone(zone)

    return NudgeContext(
        now = now,
        packageName = current.packageName,
        sessionStartedAt = current.startedAt,
        dayStartedAt = dayStartedAt,
        nightStartedAt = nightStartedAt,
        dailyUsageMs = dailyUsageMs,
        lateNightUsageMs = lateNightUsageMs,
        localHour = local.hour,
        weekday = local.dayOfWeek.value,
        pastNudges = pastNudges(events),
    )
}

/**
 * The foreground app as of the last foreground/background event, or null if the last one sent an
 * app to the background (screen off, home screen). `ACTIVITY_PAUSED` / `ACTIVITY_RESUMED` fire on
 * every activity switch, so pieces of the same app less than [mergeGapMs] apart are merged.
 */
fun currentSession(
    events: List<EventEntity>,
    mergeGapMs: Long,
): CurrentSession? {
    var packageName: String? = null
    var startedAt = 0L
    var pieceStartedAt = 0L
    var backgroundedAt: Long? = null
    for (event in events) {
        val eventPackage = event.packageName ?: continue
        when (event.eventType) {
            EVENT_TYPE_APP_FOREGROUND -> {
                val resumesRun =
                    eventPackage == packageName &&
                        backgroundedAt?.let { event.timestamp - it <= mergeGapMs } == true
                if (!resumesRun) {
                    packageName = eventPackage
                    startedAt = event.timestamp
                }
                pieceStartedAt = event.timestamp
                backgroundedAt = null
            }

            EVENT_TYPE_APP_BACKGROUND -> {
                if (eventPackage == packageName) backgroundedAt = event.timestamp
            }
        }
    }
    val current = packageName ?: return null
    if (backgroundedAt != null) return null
    return CurrentSession(current, startedAt, pieceStartedAt)
}

/** Start of the late-night window containing [now], or null outside of it. */
fun nightStartedAt(
    now: Long,
    zone: ZoneId,
    config: NudgeConfig,
): Long? {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val nightDate =
        when {
            local.hour >= config.lateNightStartHour -> local.toLocalDate()
            local.hour < config.lateNightEndHour -> local.toLocalDate().minusDays(1)
            else -> return null
        }
    return nightDate
        .atTime(config.lateNightStartHour, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

/** Shown and held-out nudges found in [events], with the user's response when there is one. */
fun pastNudges(events: List<EventEntity>): List<PastNudge> {
    val responses =
        events
            .filter { it.eventType == EVENT_TYPE_NUDGE_RESPONSE }
            .mapNotNull { event ->
                val metadata = decodeOrNull<NudgeResponseMetadata>(event.metadata) ?: return@mapNotNull null
                val response = NudgeResponse.fromId(metadata.response) ?: return@mapNotNull null
                metadata.nudgeId to (response to event.timestamp)
            }.toMap()

    return events.mapNotNull { event ->
        val shown =
            when (event.eventType) {
                EVENT_TYPE_NUDGE_SHOWN -> true
                EVENT_TYPE_NUDGE_SUPPRESSED -> false
                else -> return@mapNotNull null
            }
        val metadata = decodeOrNull<NudgeDecisionMetadata>(event.metadata) ?: return@mapNotNull null
        if (!shown && SuppressionReason.fromId(metadata.reason) != SuppressionReason.Holdout) return@mapNotNull null
        val rule = NudgeRule.fromId(metadata.ruleId) ?: return@mapNotNull null
        val packageName = event.packageName ?: return@mapNotNull null
        val response = responses[metadata.nudgeId]
        PastNudge(
            nudgeId = metadata.nudgeId,
            timestamp = event.timestamp,
            packageName = packageName,
            rule = rule,
            level = metadata.level,
            shown = shown,
            response = response?.first,
            respondedAt = response?.second,
        )
    }
}

internal fun UsageSession.overlapMs(
    from: Long,
    until: Long,
): Long = (minOf(end, until) - maxOf(start, from)).coerceAtLeast(0L)
