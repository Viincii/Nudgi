package com.vincentmignot.nudgi.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUiStateTest {
    @Test
    fun `is not complete unless every permission is granted`() {
        assertFalse(OnboardingUiState().isComplete)
        assertFalse(OnboardingUiState(hasUsageAccess = true, hasAccessibilityAccess = true).isComplete)
        assertFalse(OnboardingUiState(hasUsageAccess = true, hasNotifications = true).isComplete)
        assertFalse(OnboardingUiState(hasAccessibilityAccess = true, hasNotifications = true).isComplete)
    }

    @Test
    fun `is complete once every permission is granted`() {
        assertTrue(
            OnboardingUiState(hasUsageAccess = true, hasAccessibilityAccess = true, hasNotifications = true).isComplete,
        )
    }
}
