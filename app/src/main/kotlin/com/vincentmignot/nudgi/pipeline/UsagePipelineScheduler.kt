package com.vincentmignot.nudgi.pipeline

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "usage_pipeline"

/**
 * The periodic work scheduled before the pipeline existed, whose worker class is gone. KEEP would
 * leave it failing forever, so it is cancelled explicitly.
 */
private const val LEGACY_UNIQUE_WORK_NAME = "usage_polling"

/** 15 minutes is [androidx.work.PeriodicWorkRequest]'s minimum repeat interval. */
private const val PIPELINE_INTERVAL_MINUTES = 15L

/**
 * Enqueues the periodic pipeline run if it isn't already scheduled. Safe to call on every app
 * start: [ExistingPeriodicWorkPolicy.KEEP] leaves an already-running schedule untouched, and the
 * poller itself no-ops until usage access is granted.
 */
fun scheduleUsagePipeline(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(LEGACY_UNIQUE_WORK_NAME)
    val request =
        PeriodicWorkRequestBuilder<UsagePipelineWorker>(PIPELINE_INTERVAL_MINUTES, TimeUnit.MINUTES).build()
    workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
