package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.mascot.MascotMood

data class HomeUiState(
    val mood: MascotMood = MascotMood.Happy,
)
