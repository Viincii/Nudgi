package com.vincentmignot.nudgi.feature.settings

import android.net.Uri
import com.vincentmignot.nudgi.core.export.ExportSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val destination = Uri.parse("content://documents/nudgi-export.zip")

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
                SettingsViewModel {
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
            val viewModel = SettingsViewModel { ExportSummary(eventCount = 42, dailyStatsCount = 3) }

            viewModel.onExportDestinationChosen(destination)

            assertEquals(ExportState.Done(eventCount = 42), viewModel.uiState.value.export)
        }

    @Test
    fun `a failed export is reported`() =
        runTest(dispatcher) {
            val viewModel = SettingsViewModel { throw IOException("disk full") }

            viewModel.onExportDestinationChosen(destination)

            assertEquals(ExportState.Failed, viewModel.uiState.value.export)
        }

    @Test
    fun `a second destination is ignored while an export is running`() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<ExportSummary>()
            var calls = 0
            val viewModel =
                SettingsViewModel {
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
}
