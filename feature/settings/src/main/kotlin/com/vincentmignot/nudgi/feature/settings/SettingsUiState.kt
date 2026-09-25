package com.vincentmignot.nudgi.feature.settings

data class SettingsUiState(
    val export: ExportState = ExportState.Idle,
)

sealed interface ExportState {
    data object Idle : ExportState

    data object InProgress : ExportState

    data class Done(
        val eventCount: Int,
    ) : ExportState

    data object Failed : ExportState
}
