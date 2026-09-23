package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EventDao
import javax.inject.Inject

/** How far back the first poll after install (or after data was cleared) looks, to avoid a large backfill. */
private const val INITIAL_LOOKBACK_MS = 15 * 60 * 1000L

/**
 * How far before the poll window events are re-read, only to find the foreground event that opened a
 * session still running when the window starts. Without it, every session spanning two polls would
 * be recorded with a zero duration. Sessions longer than this still report zero.
 */
internal const val SESSION_LOOKBACK_MS = 6 * 60 * 60 * 1000L

class UsageStatsPoller
    @Inject
    constructor(
        private val eventsSource: UsageEventsSource,
        private val eventDao: EventDao,
        private val pollState: UsagePollState,
        private val permissionChecker: UsageAccessPermissionChecker,
    ) {
        /**
         * Queries usage events since the last poll and appends them to [EventDao]; a no-op without usage
         * access. Returns the start of the polled window, or null when nothing was polled.
         */
        suspend fun poll(now: Long = System.currentTimeMillis()): Long? {
            if (!permissionChecker.isGranted()) return null

            val start = pollState.lastPolledUntil() ?: (now - INITIAL_LOOKBACK_MS)
            if (start >= now) return null

            val rawEvents = eventsSource.queryEvents(start - SESSION_LOOKBACK_MS, now)
            val newEvents = mapToEventEntities(rawEvents).filter { it.timestamp >= start }
            if (newEvents.isNotEmpty()) {
                eventDao.insertAll(newEvents)
            }
            pollState.setLastPolledUntil(now)
            return start
        }
    }
