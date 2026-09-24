package com.vincentmignot.nudgi.core.today

import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_OUTCOME
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.nudge.NudgeCandidate
import com.vincentmignot.nudgi.core.nudge.NudgeConfig
import com.vincentmignot.nudgi.core.nudge.NudgeContext
import com.vincentmignot.nudgi.core.nudge.NudgeDecision
import com.vincentmignot.nudgi.core.nudge.NudgeRule
import com.vincentmignot.nudgi.core.nudge.SuppressionReason
import com.vincentmignot.nudgi.core.nudge.decisionEvent
import java.time.LocalDateTime
import java.time.ZoneId

val PARIS: ZoneId = ZoneId.of("Europe/Paris")
const val FEED = "com.example.feed"
const val VIDEO = "com.example.video"
const val LAUNCHER = "com.example.launcher"
const val MINUTE_MS = 60_000L

fun at(dateTime: String): Long =
    LocalDateTime
        .parse(dateTime)
        .atZone(PARIS)
        .toInstant()
        .toEpochMilli()

fun stats(
    date: String,
    packageName: String,
    usageMinutes: Long,
) = DailyStatsEntity(date = date, packageName = packageName, usageMs = usageMinutes * MINUTE_MS, nudgeCount = 0)

private fun contextAt(
    timestamp: Long,
    packageName: String,
) = NudgeContext(
    now = timestamp,
    packageName = packageName,
    sessionStartedAt = timestamp - 20 * MINUTE_MS,
    dayStartedAt = timestamp,
    nightStartedAt = null,
    dailyUsageMs = 20 * MINUTE_MS,
    lateNightUsageMs = 0,
    localHour = 12,
    weekday = 1,
    pastNudges = emptyList(),
)

private val longSession = NudgeCandidate(NudgeRule.LongSession, level = 1, thresholdMs = 20 * MINUTE_MS)

fun shown(
    nudgeId: String,
    timestamp: Long,
    packageName: String = FEED,
): EventEntity =
    decisionEvent(NudgeDecision.Show(longSession), contextAt(timestamp, packageName), NudgeConfig(), nudgeId)

fun heldOut(
    nudgeId: String,
    timestamp: Long,
    packageName: String = FEED,
): EventEntity =
    decisionEvent(
        NudgeDecision.Suppress(longSession, SuppressionReason.Holdout),
        contextAt(timestamp, packageName),
        NudgeConfig(),
        nudgeId,
    )

fun outcome(
    nudgeId: String,
    timestamp: Long,
    leftApp: Boolean,
    packageName: String = FEED,
) = EventEntity(
    timestamp = timestamp,
    eventType = EVENT_TYPE_NUDGE_OUTCOME,
    packageName = packageName,
    durationMs = 0,
    metadata = """{"nudge_id":"$nudgeId","left_app":$leftApp,"window_ms":600000}""",
)
