package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EventDao
import java.time.ZoneId
import javax.inject.Inject

/**
 * Refreshes `daily_stats` from `events`. Every day touched by a session closed, or a nudge shown,
 * since a given instant is recomputed from scratch rather than incremented, so running it twice
 * over the same events is harmless.
 */
class DailyStatsAggregator
    @Inject
    constructor(
        private val eventDao: EventDao,
        private val dailyStatsDao: DailyStatsDao,
    ) {
        suspend fun aggregateSince(
            since: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ) {
            val newSessions = sessionsFrom(eventDao.ofTypeSince(EVENT_TYPE_APP_BACKGROUND, since))
            val newNudges = eventDao.ofTypeSince(EVENT_TYPE_NUDGE_SHOWN, since)
            val touchedDays =
                newSessions.map { localDateOf(it.start, zone) } + newNudges.map { localDateOf(it.timestamp, zone) }
            val firstDay = touchedDays.minOrNull() ?: return

            // Any session overlapping firstDay or a later day ends after firstDay starts, so this
            // one query holds every session needed to recompute those days in full.
            val firstDayStart = firstDay.startMillis(zone)
            val usage = dailyUsage(sessionsFrom(eventDao.ofTypeSince(EVENT_TYPE_APP_BACKGROUND, firstDayStart)), zone)
            val nudgeCounts =
                eventDao
                    .ofTypeSince(EVENT_TYPE_NUDGE_SHOWN, firstDayStart)
                    .mapNotNull { event ->
                        event.packageName?.let { DailyUsageKey(localDateOf(event.timestamp, zone), it) }
                    }.groupingBy { it }
                    .eachCount()

            val rows =
                (usage.keys + nudgeCounts.keys)
                    .filter { it.date >= firstDay }
                    .map { key ->
                        DailyStatsEntity(
                            date = key.date.toString(),
                            packageName = key.packageName,
                            usageMs = usage[key] ?: 0L,
                            nudgeCount = nudgeCounts[key] ?: 0,
                        )
                    }
            dailyStatsDao.upsertAll(rows)
        }
    }
