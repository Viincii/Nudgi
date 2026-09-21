package com.vincentmignot.nudgi.feature.home

import androidx.lifecycle.ViewModel
import com.vincentmignot.nudgi.core.mascot.MascotMood
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        fun onMoodSelected(mood: MascotMood) {
            _uiState.update { it.copy(mood = mood) }
        }
    }
