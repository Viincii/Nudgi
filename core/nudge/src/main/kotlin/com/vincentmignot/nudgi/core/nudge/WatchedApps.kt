package com.vincentmignot.nudgi.core.nudge

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

fun interface WatchedApps {
    fun isWatched(packageName: String): Boolean
}

/**
 * Apps some users declare in a category the rules do not watch, or in none at all. Checked in
 * addition to the category, not instead of it.
 */
private val KNOWN_FEED_APPS =
    setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android",
    )

/**
 * Apps declared in a watched category that are not feeds. Messaging apps call themselves social,
 * but a conversation is not doom-scrolling, and nudging it would only teach the model noise.
 */
private val NEVER_WATCHED =
    setOf(
        "com.google.android.apps.messaging",
    )

private val WATCHED_CATEGORIES =
    setOf(ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_NEWS)

/** Watches social, video and news apps, plus [KNOWN_FEED_APPS]; never Nudgi itself nor [NEVER_WATCHED]. */
@Singleton
class CategoryWatchedApps
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WatchedApps {
        // Categories only change with an app update, so the answer is kept for the process lifetime.
        private val cache = ConcurrentHashMap<String, Boolean>()

        override fun isWatched(packageName: String): Boolean = cache.getOrPut(packageName) { lookUp(packageName) }

        private fun lookUp(packageName: String): Boolean {
            if (packageName == context.packageName || packageName in NEVER_WATCHED) return false
            if (packageName in KNOWN_FEED_APPS) return true
            val info =
                try {
                    context.packageManager.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return false
                }
            return info.category in WATCHED_CATEGORIES
        }
    }
