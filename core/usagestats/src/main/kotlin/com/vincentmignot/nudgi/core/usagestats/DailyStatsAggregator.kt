package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventDao
import java.time.ZoneId
import javax.inject.Inject

/**
 * Refreshes `daily_stats` from `events`. Every day touched by a session closed since a given
 * instant is recomputed from scratch rather than incremented, so running it twice over the same
 * events is harmless.
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
            if (newSessions.isEmpty()) return

            // Any session overlapping firstDay or a later day ends after firstDay starts, so this
            // one query holds every session needed to recompute those days in full.
            val firstDay = newSessions.minOf { localDateOf(it.start, zone) }
            val sessions = sessionsFrom(eventDao.ofTypeSince(EVENT_TYPE_APP_BACKGROUND, firstDay.startMillis(zone)))

            val rows =
                dailyUsage(sessions, zone)
                    .filterKeys { it.date >= firstDay }
                    .map { (key, usageMs) ->
                        // Nudges are not recorded as events yet, so there is nothing to count.
                        DailyStatsEntity(
                            date = key.date.toString(),
                            packageName = key.packageName,
                            usageMs = usageMs,
                            nudgeCount = 0,
                        )
                    }
            dailyStatsDao.upsertAll(rows)
        }
    }
