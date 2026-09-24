package com.vincentmignot.nudgi.core.today

import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.nudge.AppLabels
import com.vincentmignot.nudgi.core.nudge.WatchedApps
import com.vincentmignot.nudgi.core.usagestats.startMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import java.time.ZoneId
import javax.inject.Inject

fun interface TodayRepository {
    /** The current day, re-emitted whenever the pipeline writes and again at each midnight. */
    fun observe(): Flow<Today>
}

/**
 * Follows today's `daily_stats` and nudge events, and switches to the new day at midnight. Room
 * re-emits whenever the pipeline writes, so collectors stay current.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomTodayRepository internal constructor(
    private val dailyStatsDao: DailyStatsDao,
    private val eventDao: EventDao,
    private val watchedApps: WatchedApps,
    private val appLabels: AppLabels,
    private val clock: () -> Long,
    private val zone: ZoneId,
    // Watched-app and label lookups go through PackageManager, which is not for the main thread.
    private val workDispatcher: CoroutineDispatcher,
) : TodayRepository {
    @Inject
    constructor(
        dailyStatsDao: DailyStatsDao,
        eventDao: EventDao,
        watchedApps: WatchedApps,
        appLabels: AppLabels,
    ) : this(
        dailyStatsDao,
        eventDao,
        watchedApps,
        appLabels,
        System::currentTimeMillis,
        ZoneId.systemDefault(),
        Dispatchers.Default,
    )

    override fun observe(): Flow<Today> =
        localDates(clock, zone)
            .flatMapLatest { date ->
                combine(
                    dailyStatsDao.observeForDate(date.toString()),
                    eventDao.observeOfTypesBetween(
                        TODAY_EVENT_TYPES,
                        date.startMillis(zone),
                        date.plusDays(1).startMillis(zone),
                    ),
                ) { stats, events ->
                    todayOf(stats, events, watchedApps::isWatched, appLabels::labelOf, zone)
                }
            }.flowOn(workDispatcher)
}
