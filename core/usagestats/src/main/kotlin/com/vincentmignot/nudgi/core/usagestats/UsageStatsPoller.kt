package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EventDao
import javax.inject.Inject

/** How far back the first poll after install (or after data was cleared) looks, to avoid a large backfill. */
private const val INITIAL_LOOKBACK_MS = 15 * 60 * 1000L

class UsageStatsPoller
    @Inject
    constructor(
        private val eventsSource: UsageEventsSource,
        private val eventDao: EventDao,
        private val pollState: UsagePollState,
        private val permissionChecker: UsageAccessPermissionChecker,
    ) {
        /** Queries usage events since the last poll and appends them to [EventDao]; a no-op without usage access. */
        suspend fun poll(now: Long = System.currentTimeMillis()) {
            if (!permissionChecker.isGranted()) return

            val start = pollState.lastPolledUntil() ?: (now - INITIAL_LOOKBACK_MS)
            if (start >= now) return

            val rawEvents = eventsSource.queryEvents(start, now)
            if (rawEvents.isNotEmpty()) {
                eventDao.insertAll(mapToEventEntities(rawEvents))
            }
            pollState.setLastPolledUntil(now)
        }
    }
