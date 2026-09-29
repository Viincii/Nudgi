package com.vincentmignot.nudgi.feature.settings

data class SettingsUiState(
    val export: ExportState = ExportState.Idle,
    /** End of the running friction pause, or null when friction is not paused. */
    val frictionPausedUntil: Long? = null,
)

sealed interface ExportState {
    data object Idle : ExportState

    data object InProgress : ExportState

    data class Done(
        val eventCount: Int,
    ) : ExportState

    data object Failed : ExportState
}
