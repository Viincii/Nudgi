package com.vincentmignot.nudgi.core.nudge

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The name an app shows in the launcher, falling back to its package name. */
fun interface AppLabels {
    fun labelOf(packageName: String): String
}

class PackageManagerAppLabels
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppLabels {
        override fun labelOf(packageName: String): String {
            val packageManager = context.packageManager
            return try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }
