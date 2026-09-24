package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.today.TodayNudge

data class HomeUiState(
    val mood: MascotMood = MascotMood.Happy,
    /** Set when a debug build forces [mood] instead of deriving it from today's data. */
    val isMoodOverridden: Boolean = false,
    /** False until today's data has been read once, so no empty summary flashes on launch. */
    val isLoaded: Boolean = false,
    /** Today's time on watched apps, from sessions already closed. */
    val watchedUsageMs: Long = 0L,
    /** Nudges shown today, oldest first. Held-out nudges were never seen and are left out. */
    val nudges: List<TodayNudge> = emptyList(),
)
