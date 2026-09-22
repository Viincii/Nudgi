package com.vincentmignot.nudgi.feature.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageAccessPermissionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `is not granted when the app op is denied`() {
        setUsageStatsOpMode(AppOpsManager.MODE_IGNORED)

        assertFalse(isUsageAccessGranted(context))
    }

    @Test
    fun `is granted once the app op is allowed`() {
        setUsageStatsOpMode(AppOpsManager.MODE_ALLOWED)

        assertTrue(isUsageAccessGranted(context))
    }

    private fun setUsageStatsOpMode(mode: Int) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)
    }
}
