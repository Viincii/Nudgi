package com.vincentmignot.nudgi.core.usagestats

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface UsageAccessPermissionChecker {
    fun isGranted(): Boolean
}

class SystemUsageAccessPermissionChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UsageAccessPermissionChecker {
        override fun isGranted(): Boolean = isUsageAccessGranted(context)
    }
