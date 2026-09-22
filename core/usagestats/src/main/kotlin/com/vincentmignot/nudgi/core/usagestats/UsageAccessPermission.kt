package com.vincentmignot.nudgi.core.usagestats

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/** Whether the user has granted usage access from system settings; there is no runtime dialog for it. */
fun isUsageAccessGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode =
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    return mode == AppOpsManager.MODE_ALLOWED
}
