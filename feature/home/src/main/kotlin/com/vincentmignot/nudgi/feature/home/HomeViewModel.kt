package com.vincentmignot.nudgi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.nudge.AppLabels
import com.vincentmignot.nudgi.core.nudge.WatchedApps
import com.vincentmignot.nudgi.core.usagestats.startMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/**
 * Follows today's `daily_stats` and nudge events, and switches to the new day at midnight. Room
 * re-emits whenever the pipeline writes, so the screen stays current while it is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel internal constructor(
    dailyStatsDao: DailyStatsDao,
    eventDao: EventDao,
    watchedApps: WatchedApps,
    appLabels: AppLabels,
    clock: () -> Long,
    zone: ZoneId,
    // Watched-app and label lookups go through PackageManager, which is not for the main thread.
    workDispatcher: CoroutineDispatcher,
) : ViewModel() {
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

    private val moodOverride = MutableStateFlow<MascotMood?>(null)

    private val today =
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
                    todayUiState(stats, events, watchedApps::isWatched, appLabels::labelOf, zone)
                }
            }.flowOn(workDispatcher)

    val uiState: StateFlow<HomeUiState> =
        combine(today, moodOverride) { state, override ->
            if (override == null) state else state.copy(mood = override, isMoodOverridden = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Forces the mascot's [mood] in debug builds; null goes back to the mood derived from today. */
    fun onMoodSelected(mood: MascotMood?) {
        moodOverride.value = mood
    }
}
