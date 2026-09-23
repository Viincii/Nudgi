package com.vincentmignot.nudgi.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UsagePipelineWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val pipeline: UsagePipeline,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            pipeline.run()
            return Result.success()
        }
    }
