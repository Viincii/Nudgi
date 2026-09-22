package com.vincentmignot.nudgi.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUiStateTest {
    @Test
    fun `is not complete unless both permissions are granted`() {
        assertFalse(OnboardingUiState().isComplete)
        assertFalse(OnboardingUiState(hasUsageAccess = true).isComplete)
        assertFalse(OnboardingUiState(hasAccessibilityAccess = true).isComplete)
    }

    @Test
    fun `is complete once both permissions are granted`() {
        assertTrue(OnboardingUiState(hasUsageAccess = true, hasAccessibilityAccess = true).isComplete)
    }
}
