package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SUPPRESSED
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeEventDao : EventDao {
    val events = mutableListOf<EventEntity>()

    override suspend fun insert(event: EventEntity): Long {
        events += event
        return events.size.toLong()
    }

    override suspend fun insertAll(events: List<EventEntity>) {
        this.events += events
    }

    override fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>> = flowOf(events.filter { it.timestamp in startInclusive until endExclusive })

    override suspend fun since(sinceInclusive: Long): List<EventEntity> =
        events.filter { it.timestamp >= sinceInclusive }.sortedBy { it.timestamp }

    override suspend fun ofTypeSince(
        eventType: String,
        sinceInclusive: Long,
    ): List<EventEntity> = since(sinceInclusive).filter { it.eventType == eventType }

    override suspend fun latest(limit: Int): List<EventEntity> = events.takeLast(limit)
}

private class FakeNotifier(
    var enabled: Boolean = true,
) : NudgeNotifier {
    val shown = mutableListOf<Pair<String, NudgeCandidate>>()

    override fun canNotify() = enabled

    override fun show(
        nudgeId: String,
        candidate: NudgeCandidate,
        nudgeContext: NudgeContext,
    ) {
        shown += nudgeId to candidate
    }
}

class NudgeEvaluatorTest {
    private val eventDao = FakeEventDao()
    private val notifier = FakeNotifier()
    private var draw = 0.5

    private val evaluator =
        NudgeEvaluator(
            eventDao = eventDao,
            watchedApps = { it == FEED },
            notifier = notifier,
            holdoutDraw = { draw },
            config = NudgeConfig(),
        )

    private val sessionStart = at("2026-09-22T12:00:00")

    private fun decisions() =
        eventDao.events.filter {
            it.eventType == EVENT_TYPE_NUDGE_SHOWN ||
                it.eventType == EVENT_TYPE_NUDGE_SUPPRESSED
        }

    @Test
    fun `shows and records a nudge once a rule fires`() =
        runTest {
            eventDao.insert(foreground(sessionStart))

            evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)

            val recorded = decisions().single()
            assertEquals(EVENT_TYPE_NUDGE_SHOWN, recorded.eventType)
            assertEquals(FEED, recorded.packageName)
            val metadata = NudgeJson.decodeFromString<NudgeDecisionMetadata>(recorded.metadata)
            assertEquals(notifier.shown.single().first, metadata.nudgeId)
            assertEquals("long_session", metadata.ruleId)
        }

    @Test
    fun `does not show the same level twice`() =
        runTest {
            eventDao.insert(foreground(sessionStart))

            evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)
            evaluator.evaluate(now = sessionStart + 33 * MINUTE_MS, zone = PARIS)

            assertEquals(1, notifier.shown.size)
            assertEquals(1, decisions().size)
        }

    @Test
    fun `records a holdout without notifying`() =
        runTest {
            eventDao.insert(foreground(sessionStart))
            draw = 0.01

            evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)

            assertEquals(EVENT_TYPE_NUDGE_SUPPRESSED, decisions().single().eventType)
            assertEquals(emptyList<Pair<String, NudgeCandidate>>(), notifier.shown)
        }

    @Test
    fun `records a suppression when notifications are off`() =
        runTest {
            eventDao.insert(foreground(sessionStart))
            notifier.enabled = false

            evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)

            val metadata = NudgeJson.decodeFromString<NudgeDecisionMetadata>(decisions().single().metadata)
            assertEquals("notifications_disabled", metadata.reason)
            assertEquals(emptyList<Pair<String, NudgeCandidate>>(), notifier.shown)
        }

    @Test
    fun `returns when to evaluate again`() =
        runTest {
            eventDao.insert(foreground(sessionStart))

            val beforeThreshold = evaluator.evaluate(now = sessionStart + 5 * MINUTE_MS, zone = PARIS)
            val afterNudge = evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)

            assertEquals(sessionStart + 20 * MINUTE_MS, beforeThreshold)
            assertEquals(sessionStart + 35 * MINUTE_MS, afterNudge)
        }

    @Test
    fun `records nothing for an unwatched app`() =
        runTest {
            eventDao.insert(foreground(sessionStart, packageName = OTHER))

            val next = evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)

            assertEquals(1, eventDao.events.size)
            assertNull(next)
        }
}
