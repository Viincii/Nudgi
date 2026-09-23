package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_FOREGROUND
import com.vincentmignot.nudgi.core.database.EventEntity
import java.time.LocalDateTime
import java.time.ZoneId

val PARIS: ZoneId = ZoneId.of("Europe/Paris")
const val FEED = "com.example.feed"
const val OTHER = "com.example.other"
const val MINUTE_MS = 60_000L

fun at(dateTime: String): Long =
    LocalDateTime
        .parse(dateTime)
        .atZone(PARIS)
        .toInstant()
        .toEpochMilli()

fun foreground(
    timestamp: Long,
    packageName: String = FEED,
) = EventEntity(
    timestamp = timestamp,
    eventType = EVENT_TYPE_APP_FOREGROUND,
    packageName = packageName,
    durationMs = 0,
    metadata = "{}",
)

fun background(
    timestamp: Long,
    durationMs: Long,
    packageName: String = FEED,
) = EventEntity(
    timestamp = timestamp,
    eventType = EVENT_TYPE_APP_BACKGROUND,
    packageName = packageName,
    durationMs = durationMs,
    metadata = "{}",
)

/** Foreground and background events of one closed session, as the poller records them. */
fun closedSession(
    start: Long,
    end: Long,
    packageName: String = FEED,
) = listOf(foreground(start, packageName), background(end, end - start, packageName))
