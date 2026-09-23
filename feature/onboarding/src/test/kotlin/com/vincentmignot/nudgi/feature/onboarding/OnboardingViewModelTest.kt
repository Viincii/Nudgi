package com.vincentmignot.nudgi.feature.onboarding

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.vincentmignot.nudgi.core.accessibility.NudgiAccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `starts with neither permission granted in a fresh test environment`() {
        val viewModel = OnboardingViewModel(context)

        assertFalse(viewModel.uiState.value.isComplete)
    }

    @Test
    fun `refresh re-reads the accessibility permission`() {
        val viewModel = OnboardingViewModel(context)
        assertFalse(viewModel.uiState.value.hasAccessibilityAccess)

        val enabledService = ComponentName(context, NudgiAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            enabledService,
        )
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.hasAccessibilityAccess)
    }

    @Test
    fun `refresh re-reads whether notifications are enabled`() {
        val notificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))
        notificationManager.setNotificationsEnabled(false)
        val viewModel = OnboardingViewModel(context)
        assertFalse(viewModel.uiState.value.hasNotifications)

        notificationManager.setNotificationsEnabled(true)
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.hasNotifications)
    }
}
