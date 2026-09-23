package com.vincentmignot.nudgi.pipeline

import com.vincentmignot.nudgi.core.nudge.NudgeEvaluator
import com.vincentmignot.nudgi.core.usagestats.DailyStatsAggregator
import com.vincentmignot.nudgi.core.usagestats.UsageStatsPoller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls usage events, refreshes `daily_stats`, then evaluates the nudge rules, all against the same
 * instant. Runs are serialized: two overlapping polls would read the same window and insert its
 * events twice.
 */
@Singleton
class UsagePipeline
    @Inject
    constructor(
        private val poller: UsageStatsPoller,
        private val aggregator: DailyStatsAggregator,
        private val evaluator: NudgeEvaluator,
    ) {
        private val mutex = Mutex()

        suspend fun run() =
            mutex.withLock {
                val now = System.currentTimeMillis()
                val windowStart = poller.poll(now) ?: return@withLock
                aggregator.aggregateSince(windowStart)
                evaluator.evaluate(now)
            }
    }
