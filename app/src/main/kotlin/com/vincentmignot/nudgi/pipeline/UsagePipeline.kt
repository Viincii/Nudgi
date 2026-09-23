package com.vincentmignot.nudgi.pipeline

import com.vincentmignot.nudgi.core.nudge.NudgeEvaluator
import com.vincentmignot.nudgi.core.usagestats.DailyStatsAggregator
import com.vincentmignot.nudgi.core.usagestats.UsageStatsPoller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface PipelineRunner {
    /** Runs the pipeline once; returns when the nudge rules should next be evaluated, if ever. */
    suspend fun run(): Long?
}

/**
 * Polls usage events, refreshes `daily_stats`, then evaluates the nudge rules, all against the same
 * instant. Runs are serialized: two overlapping polls would read the same window and insert its
 * events twice. A run is never cancelled halfway either, since a poll cancelled between inserting
 * its events and saving its window would insert them again on the next run.
 */
@Singleton
class UsagePipeline
    @Inject
    constructor(
        private val poller: UsageStatsPoller,
        private val aggregator: DailyStatsAggregator,
        private val evaluator: NudgeEvaluator,
    ) : PipelineRunner {
        private val mutex = Mutex()

        override suspend fun run(): Long? =
            withContext(Dispatchers.IO + NonCancellable) {
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    val windowStart = poller.poll(now) ?: return@withLock null
                    aggregator.aggregateSince(windowStart)
                    evaluator.evaluate(now)
                }
            }
    }
