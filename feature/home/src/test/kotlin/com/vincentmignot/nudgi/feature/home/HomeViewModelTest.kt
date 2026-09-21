package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `nudgi starts happy`() {
        val viewModel = HomeViewModel()

        assertEquals(MascotMood.Happy, viewModel.uiState.value.mood)
    }

    @Test
    fun `selecting a mood updates the state`() {
        val viewModel = HomeViewModel()

        viewModel.onMoodSelected(MascotMood.Worried)

        assertEquals(MascotMood.Worried, viewModel.uiState.value.mood)
    }
}
