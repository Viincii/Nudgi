package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.today.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val HOUR_MS = 3_600_000L

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = MutableSharedFlow<Today>(replay = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): HomeViewModel {
        val viewModel = HomeViewModel { today }
        // WhileSubscribed only follows the repository while someone collects the state.
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `nothing is shown before today's data is read`() =
        runTest(dispatcher) {
            assertFalse(viewModel().uiState.value.isLoaded)
        }

    @Test
    fun `shows the day and its mood`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            today.emit(Today(watchedUsageMs = HOUR_MS, nudges = emptyList()))

            val state = viewModel.uiState.value
            assertTrue(state.isLoaded)
            assertEquals(HOUR_MS, state.watchedUsageMs)
            assertEquals(MascotMood.Neutral, state.mood)
        }

    @Test
    fun `a debug override replaces the mood until it is cleared`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            today.emit(Today(watchedUsageMs = 0L, nudges = emptyList()))

            viewModel.onMoodSelected(MascotMood.Worried)
            assertEquals(MascotMood.Worried, viewModel.uiState.value.mood)
            assertTrue(viewModel.uiState.value.isMoodOverridden)

            viewModel.onMoodSelected(null)
            assertEquals(MascotMood.Happy, viewModel.uiState.value.mood)
            assertFalse(viewModel.uiState.value.isMoodOverridden)
        }
}
