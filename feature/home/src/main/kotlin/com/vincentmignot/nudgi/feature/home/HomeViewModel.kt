package com.vincentmignot.nudgi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.today.TodayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Shows the current day as [TodayRepository] follows it, with a debug-only mood override on top. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        todayRepository: TodayRepository,
    ) : ViewModel() {
        private val moodOverride = MutableStateFlow<MascotMood?>(null)

        val uiState: StateFlow<HomeUiState> =
            combine(todayRepository.observe(), moodOverride) { today, override ->
                HomeUiState(
                    mood = override ?: today.mood,
                    isMoodOverridden = override != null,
                    isLoaded = true,
                    watchedUsageMs = today.watchedUsageMs,
                    nudges = today.nudges,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

        /** Forces the mascot's [mood] in debug builds; null goes back to the mood derived from today. */
        fun onMoodSelected(mood: MascotMood?) {
            moodOverride.value = mood
        }
    }
