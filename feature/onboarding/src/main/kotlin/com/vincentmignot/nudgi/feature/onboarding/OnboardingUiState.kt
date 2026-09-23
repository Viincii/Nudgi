package com.vincentmignot.nudgi.feature.onboarding

data class OnboardingUiState(
    val hasUsageAccess: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val hasNotifications: Boolean = false,
) {
    val isComplete: Boolean get() = hasUsageAccess && hasAccessibilityAccess && hasNotifications
}
