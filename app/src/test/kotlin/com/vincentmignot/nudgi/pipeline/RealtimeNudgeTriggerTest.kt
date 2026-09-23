package com.vincentmignot.nudgi.pipeline

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val FEED = "com.example.feed"
private const val LAUNCHER = "com.example.launcher"
private const val MINUTE_MS = 60_000L

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeNudgeTriggerTest {
    /** Records the time of each run and answers with the next scheduled times, then null. */
    private class FakePipeline(
        private val scope: TestScope,
        private val nextRuns: MutableList<Long?> = mutableListOf(),
    ) : PipelineRunner {
        val runs = mutableListOf<Long>()

        override suspend fun run(): Long? {
            runs += scope.testScheduler.currentTime
            return nextRuns.removeFirstOrNull()
        }

        fun thenRunAt(vararg times: Long?) {
            nextRuns += times.toList()
        }
    }

    private fun TestScope.trigger(pipeline: FakePipeline) =
        RealtimeNudgeTrigger(
            pipeline = pipeline,
            watchedApps = { it == FEED },
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
        )

    @Test
    fun `runs the pipeline shortly after a watched app comes to the foreground`() =
        runTest {
            val pipeline = FakePipeline(this)

            trigger(pipeline).onForegroundApp(FEED)
            advanceTimeBy(1_999)
            runCurrent()
            assertEquals(emptyList<Long>(), pipeline.runs)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(2_000L), pipeline.runs)
        }

    @Test
    fun `runs again at each time the pipeline asks for, until it returns null`() =
        runTest {
            val pipeline = FakePipeline(this).apply { thenRunAt(20 * MINUTE_MS, 35 * MINUTE_MS, null) }

            trigger(pipeline).onForegroundApp(FEED)
            advanceTimeBy(60 * MINUTE_MS)

            assertEquals(listOf(2_000L, 20 * MINUTE_MS, 35 * MINUTE_MS), pipeline.runs)
        }

    @Test
    fun `stops the schedule when the user leaves for another app`() =
        runTest {
            val pipeline = FakePipeline(this).apply { thenRunAt(20 * MINUTE_MS) }
            val trigger = trigger(pipeline)

            trigger.onForegroundApp(FEED)
            advanceTimeBy(5 * MINUTE_MS)
            trigger.onForegroundApp(LAUNCHER)
            advanceTimeBy(60 * MINUTE_MS)

            assertEquals(listOf(2_000L), pipeline.runs)
        }

    @Test
    fun `window changes within the same app keep the schedule`() =
        runTest {
            val pipeline = FakePipeline(this).apply { thenRunAt(20 * MINUTE_MS) }
            val trigger = trigger(pipeline)

            trigger.onForegroundApp(FEED)
            advanceTimeBy(5 * MINUTE_MS)
            trigger.onForegroundApp(FEED)
            advanceTimeBy(60 * MINUTE_MS)

            assertEquals(listOf(2_000L, 20 * MINUTE_MS), pipeline.runs)
        }

    @Test
    fun `coming back to the app after the schedule ended starts it again`() =
        runTest {
            val pipeline = FakePipeline(this)
            val trigger = trigger(pipeline)

            trigger.onForegroundApp(FEED)
            advanceTimeBy(5 * MINUTE_MS)
            trigger.onForegroundApp(FEED)
            advanceTimeBy(5 * MINUTE_MS)

            assertEquals(listOf(2_000L, 5 * MINUTE_MS + 2_000L), pipeline.runs)
        }

    @Test
    fun `does nothing for an app that is not watched`() =
        runTest {
            val pipeline = FakePipeline(this)

            trigger(pipeline).onForegroundApp(LAUNCHER)
            advanceTimeBy(60 * MINUTE_MS)

            assertEquals(emptyList<Long>(), pipeline.runs)
        }
}
