package com.vincentmignot.nudgi.feature.onboarding

import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vincentmignot.nudgi.core.accessibility.isAccessibilityServiceEnabled
import com.vincentmignot.nudgi.core.nudge.areNudgeNotificationsEnabled
import com.vincentmignot.nudgi.core.usagestats.isUsageAccessGranted

const val ONBOARDING_ROUTE = "onboarding"

/** Whether every permission the onboarding flow asks for is already granted. */
fun hasCompletedOnboarding(context: Context): Boolean =
    isUsageAccessGranted(context) && isAccessibilityServiceEnabled(context) && areNudgeNotificationsEnabled(context)

fun NavGraphBuilder.onboardingScreen(onOnboardingComplete: () -> Unit) {
    composable(ONBOARDING_ROUTE) {
        OnboardingRoute(onOnboardingComplete = onOnboardingComplete)
    }
}
