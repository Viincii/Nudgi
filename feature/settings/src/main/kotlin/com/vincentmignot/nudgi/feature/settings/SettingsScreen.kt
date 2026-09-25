package com.vincentmignot.nudgi.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vincentmignot.nudgi.core.designsystem.NudgiTheme
import com.vincentmignot.nudgi.core.export.EXPORT_MIME_TYPE
import com.vincentmignot.nudgi.core.export.exportFileName
import java.time.LocalDateTime

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createDocument =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
            viewModel::onExportDestinationChosen,
        )
    SettingsScreen(
        uiState = uiState,
        onExportClick = { createDocument.launch(exportFileName(LocalDateTime.now())) },
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.settings_data_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_data_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onExportClick, enabled = uiState.export != ExportState.InProgress) {
                Text(stringResource(R.string.settings_export_button))
            }
            exportStatus(uiState.export)?.let { status ->
                Text(text = status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun exportStatus(state: ExportState): String? =
    when (state) {
        ExportState.Idle -> {
            null
        }

        ExportState.InProgress -> {
            stringResource(R.string.settings_export_in_progress)
        }

        is ExportState.Done -> {
            pluralStringResource(R.plurals.settings_export_done, state.eventCount, state.eventCount)
        }

        ExportState.Failed -> {
            stringResource(R.string.settings_export_failed)
        }
    }

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    NudgiTheme {
        SettingsScreen(uiState = SettingsUiState(export = ExportState.Done(eventCount = 12_345)), onExportClick = {})
    }
}
