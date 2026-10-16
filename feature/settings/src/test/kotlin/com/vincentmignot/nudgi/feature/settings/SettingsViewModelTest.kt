package com.vincentmignot.nudgi.feature.settings

import android.net.Uri
import com.vincentmignot.nudgi.core.export.DataExporter
import com.vincentmignot.nudgi.core.export.DataImporter
import com.vincentmignot.nudgi.core.export.ExportSummary
import com.vincentmignot.nudgi.core.export.ImportException
import com.vincentmignot.nudgi.core.export.ImportProblem
import com.vincentmignot.nudgi.core.export.ImportSummary
import com.vincentmignot.nudgi.core.nudge.FrictionPause
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private class FakeFrictionPause : FrictionPause {
    var pauses = 0
    override val pausedUntil = MutableStateFlow<Long?>(null)

    override suspend fun pause() {
        pauses++
        pausedUntil.value = 1_000L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val destination = Uri.parse("content://documents/nudgi-export.zip")
    private val frictionPause = FakeFrictionPause()

    private val source = Uri.parse("content://documents/nudgi-export-20261016-0900.zip")

    private fun viewModel(
        importer: DataImporter = DataImporter { ImportSummary(addedCount = 0, alreadyPresentCount = 0) },
        exporter: DataExporter = DataExporter { ExportSummary(0, 0) },
    ) = SettingsViewModel(frictionPause, exporter, importer)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `backing out of the file picker exports nothing`() =
        runTest(dispatcher) {
            var calls = 0
            val viewModel =
                viewModel {
                    calls++
                    ExportSummary(0, 0)
                }

            viewModel.onExportDestinationChosen(null)

            assertEquals(0, calls)
            assertEquals(ExportState.Idle, viewModel.uiState.value.export)
        }

    @Test
    fun `a successful export reports the event count`() =
        runTest(dispatcher) {
            val viewModel = viewModel { ExportSummary(eventCount = 42, dailyStatsCount = 3) }

            viewModel.onExportDestinationChosen(destination)

            assertEquals(ExportState.Done(eventCount = 42), viewModel.uiState.value.export)
        }

    @Test
    fun `a failed export is reported`() =
        runTest(dispatcher) {
            val viewModel = viewModel { throw IOException("disk full") }

            viewModel.onExportDestinationChosen(destination)

            assertEquals(ExportState.Failed, viewModel.uiState.value.export)
        }

    @Test
    fun `a second destination is ignored while an export is running`() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<ExportSummary>()
            var calls = 0
            val viewModel =
                viewModel {
                    calls++
                    pending.await()
                }

            viewModel.onExportDestinationChosen(destination)
            assertEquals(ExportState.InProgress, viewModel.uiState.value.export)
            viewModel.onExportDestinationChosen(destination)
            pending.complete(ExportSummary(eventCount = 1, dailyStatsCount = 0))

            assertEquals(1, calls)
            assertEquals(ExportState.Done(eventCount = 1), viewModel.uiState.value.export)
        }

    @Test
    fun `pausing friction shows until when, and a second tap does nothing`() =
        runTest(dispatcher) {
            val viewModel = viewModel { ExportSummary(0, 0) }

            viewModel.onPauseFrictionClick()
            viewModel.onPauseFrictionClick()

            assertEquals(1, frictionPause.pauses)
            assertEquals(1_000L, viewModel.uiState.value.frictionPausedUntil)
        }

    @Test
    fun `backing out of the import picker imports nothing`() =
        runTest(dispatcher) {
            var calls = 0
            val viewModel =
                viewModel(
                    importer = {
                        calls++
                        ImportSummary(0, 0)
                    },
                )

            viewModel.onImportSourceChosen(null)

            assertEquals(0, calls)
            assertEquals(ImportState.Idle, viewModel.uiState.value.import)
        }

    @Test
    fun `a successful import reports what was added and what was already there`() =
        runTest(dispatcher) {
            val viewModel = viewModel(importer = { ImportSummary(addedCount = 40, alreadyPresentCount = 2) })

            viewModel.onImportSourceChosen(source)

            assertEquals(ImportState.Done(addedCount = 40, alreadyPresentCount = 2), viewModel.uiState.value.import)
        }

    @Test
    fun `an export from another version is reported as incompatible`() =
        runTest(dispatcher) {
            val viewModel = viewModel(importer = { throw ImportException(ImportProblem.UnsupportedSchema(2)) })

            viewModel.onImportSourceChosen(source)

            assertEquals(ImportState.Failed(ImportFailure.Incompatible), viewModel.uiState.value.import)
        }

    @Test
    fun `a truncated export is reported as not an export`() =
        runTest(dispatcher) {
            val problem = ImportProblem.CountMismatch("events.jsonl", expected = 10, actual = 4)
            val viewModel = viewModel(importer = { throw ImportException(problem) })

            viewModel.onImportSourceChosen(source)

            assertEquals(ImportState.Failed(ImportFailure.NotAnExport), viewModel.uiState.value.import)
        }

    @Test
    fun `an unreadable file is reported as a failure`() =
        runTest(dispatcher) {
            val viewModel = viewModel(importer = { throw IOException("gone") })

            viewModel.onImportSourceChosen(source)

            assertEquals(ImportState.Failed(ImportFailure.Error), viewModel.uiState.value.import)
        }

    @Test
    fun `a second file is ignored while an import is running`() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<ImportSummary>()
            var calls = 0
            val viewModel =
                viewModel(
                    importer = {
                        calls++
                        pending.await()
                    },
                )

            viewModel.onImportSourceChosen(source)
            assertEquals(ImportState.InProgress, viewModel.uiState.value.import)
            viewModel.onImportSourceChosen(source)
            pending.complete(ImportSummary(addedCount = 1, alreadyPresentCount = 0))

            assertEquals(1, calls)
            assertEquals(ImportState.Done(addedCount = 1, alreadyPresentCount = 0), viewModel.uiState.value.import)
        }
}
