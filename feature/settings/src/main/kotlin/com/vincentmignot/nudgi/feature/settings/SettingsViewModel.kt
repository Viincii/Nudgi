package com.vincentmignot.nudgi.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vincentmignot.nudgi.core.export.DataExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val dataExporter: DataExporter,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        /** Called with the document the user created in the file picker, or null if they backed out. */
        fun onExportDestinationChosen(destination: Uri?) {
            if (destination == null || _uiState.value.export == ExportState.InProgress) return
            _uiState.update { it.copy(export = ExportState.InProgress) }
            viewModelScope.launch {
                val result =
                    try {
                        ExportState.Done(eventCount = dataExporter.exportTo(destination).eventCount)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ExportState.Failed
                    }
                _uiState.update { it.copy(export = result) }
            }
        }
    }
