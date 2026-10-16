package com.vincentmignot.nudgi.feature.settings

data class SettingsUiState(
    val export: ExportState = ExportState.Idle,
    val import: ImportState = ImportState.Idle,
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

sealed interface ImportState {
    data object Idle : ImportState

    data object InProgress : ImportState

    data class Done(
        val addedCount: Int,
        val alreadyPresentCount: Int,
    ) : ImportState

    data class Failed(
        val reason: ImportFailure,
    ) : ImportState
}

enum class ImportFailure {
    /** Written by a version of Nudgi whose files or schema this one cannot read. */
    Incompatible,

    /** Not a Nudgi export, or a truncated or edited one. */
    NotAnExport,

    /** The file could not be read, or the database refused the rows. */
    Error,
}
