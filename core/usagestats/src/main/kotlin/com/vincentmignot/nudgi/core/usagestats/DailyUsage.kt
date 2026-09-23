package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One foreground session of [packageName], over `[start, end)` in epoch millis. */
data class UsageSession(
    val packageName: String,
    val start: Long,
    val end: Long,
)

data class DailyUsageKey(
    val date: LocalDate,
    val packageName: String,
)

/**
 * Rebuilds sessions from `app_background` events, whose duration is the length of the session they
 * close. Events with no duration (no matching foreground event was found) carry no usable span and
 * are dropped.
 */
fun sessionsFrom(backgroundEvents: List<EventEntity>): List<UsageSession> =
    backgroundEvents.mapNotNull { event ->
        val packageName = event.packageName ?: return@mapNotNull null
        if (event.eventType != EVENT_TYPE_APP_BACKGROUND || event.durationMs <= 0) return@mapNotNull null
        UsageSession(packageName = packageName, start = event.timestamp - event.durationMs, end = event.timestamp)
    }

fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun localDateOf(
    timestamp: Long,
    zone: ZoneId,
): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

/**
 * Sums [sessions] per local calendar day and package. A session crossing midnight is split, so each
 * day only counts the time actually spent in it; days are taken from [zone] so that DST days of 23
 * or 25 hours are handled.
 */
fun dailyUsage(
    sessions: List<UsageSession>,
    zone: ZoneId,
): Map<DailyUsageKey, Long> {
    val totals = mutableMapOf<DailyUsageKey, Long>()
    for (session in sessions) {
        var day = localDateOf(session.start, zone)
        var sliceStart = session.start
        while (sliceStart < session.end) {
            val sliceEnd = minOf(session.end, day.plusDays(1).startMillis(zone))
            val key = DailyUsageKey(day, session.packageName)
            totals[key] = (totals[key] ?: 0L) + (sliceEnd - sliceStart)
            sliceStart = sliceEnd
            day = day.plusDays(1)
        }
    }
    return totals
}
