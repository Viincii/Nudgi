package com.vincentmignot.nudgi.core.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityPermissionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val serviceComponent
        get() = ComponentName(context, NudgiAccessibilityService::class.java).flattenToString()

    private fun setEnabledServices(value: String?) {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)
    }

    @Test
    fun `returns false when no service is enabled`() {
        setEnabledServices(null)

        assertFalse(isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `returns false when only another service is enabled`() {
        setEnabledServices("com.other.app/com.other.app.SomeAccessibilityService")

        assertFalse(isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `returns true when the service is among the enabled ones`() {
        setEnabledServices("com.other.app/com.other.app.SomeAccessibilityService:$serviceComponent")

        assertTrue(isAccessibilityServiceEnabled(context))
    }
}
