package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_FOREGROUND
import com.vincentmignot.nudgi.core.database.EventEntity

/**
 * Maps chronologically-ordered [rawEvents] to [EventEntity] rows. A background event gets the
 * session length as [EventEntity.durationMs] by pairing it with the most recent foreground event
 * seen for the same package; a session with no matching foreground event (already running when the
 * polling window opened) reports a duration of zero rather than guessing when it started.
 */
fun mapToEventEntities(rawEvents: List<RawUsageEvent>): List<EventEntity> {
    val openSessions = mutableMapOf<String, Long>()
    return rawEvents.map { raw ->
        when (raw.type) {
            UsageEventType.Foreground -> {
                openSessions[raw.packageName] = raw.timestamp
                EventEntity(
                    timestamp = raw.timestamp,
                    eventType = EVENT_TYPE_APP_FOREGROUND,
                    packageName = raw.packageName,
                    durationMs = 0,
                    metadata = "{}",
                )
            }

            UsageEventType.Background -> {
                val openedAt = openSessions.remove(raw.packageName)
                val durationMs = if (openedAt != null) raw.timestamp - openedAt else 0
                EventEntity(
                    timestamp = raw.timestamp,
                    eventType = EVENT_TYPE_APP_BACKGROUND,
                    packageName = raw.packageName,
                    durationMs = durationMs,
                    metadata = "{}",
                )
            }
        }
    }
}
