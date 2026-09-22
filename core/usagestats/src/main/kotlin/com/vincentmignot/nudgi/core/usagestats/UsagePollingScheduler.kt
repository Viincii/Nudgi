package com.vincentmignot.nudgi.core.usagestats

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "usage_polling"

/** 15 minutes is [androidx.work.PeriodicWorkRequest]'s minimum repeat interval. */
private const val POLL_INTERVAL_MINUTES = 15L

/**
 * Enqueues the periodic usage-stats poll if it isn't already scheduled. Safe to call on every app
 * start: [ExistingPeriodicWorkPolicy.KEEP] leaves an already-running schedule untouched, and
 * [UsageStatsPoller] itself no-ops until usage access is granted.
 */
fun schedulePeriodicUsagePolling(context: Context) {
    val request =
        PeriodicWorkRequestBuilder<UsagePollingWorker>(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES).build()
    WorkManager
        .getInstance(context)
        .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
