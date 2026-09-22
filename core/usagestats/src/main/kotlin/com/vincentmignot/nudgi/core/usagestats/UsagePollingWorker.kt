package com.vincentmignot.nudgi.core.usagestats

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UsagePollingWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val poller: UsageStatsPoller,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            poller.poll()
            return Result.success()
        }
    }
