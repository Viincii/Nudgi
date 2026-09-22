package com.vincentmignot.nudgi.core.usagestats

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
import org.robolectric.shadows.ShadowAppOpsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageAccessPermissionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val shadowAppOps: ShadowAppOpsManager
        get() = shadowOf(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)

    @Test
    fun `returns false when usage access is not granted`() {
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED,
        )

        assertFalse(isUsageAccessGranted(context))
    }

    @Test
    fun `returns true when usage access is granted`() {
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED,
        )

        assertTrue(isUsageAccessGranted(context))
    }
}
