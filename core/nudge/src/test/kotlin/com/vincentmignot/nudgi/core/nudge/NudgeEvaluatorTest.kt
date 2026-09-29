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

    override fun observeOfTypesBetween(
        eventTypes: List<String>,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>> =
        flowOf(events.filter { it.eventType in eventTypes && it.timestamp in startInclusive until endExclusive })

    override suspend fun since(sinceInclusive: Long): List<EventEntity> =
        events.filter { it.timestamp >= sinceInclusive }.sortedBy { it.timestamp }

    override suspend fun ofTypeSince(
        eventType: String,
        sinceInclusive: Long,
    ): List<EventEntity> = since(sinceInclusive).filter { it.eventType == eventType }

    override suspend fun latest(limit: Int): List<EventEntity> = events.takeLast(limit)

    override suspend fun maxId(): Long? = error("Not used by these tests")

    override suspend fun pageByIdAfter(
        afterId: Long,
        upToId: Long,
        limit: Int,
    ): List<EventEntity> = error("Not used by these tests")
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

private class FakeFrictionPresenter(
    var available: Boolean = true,
) : FrictionPresenter {
    val presented = mutableListOf<Pair<String, FrictionLevel>>()

    override suspend fun present(
        nudgeId: String,
        candidate: NudgeCandidate,
        nudgeContext: NudgeContext,
        level: FrictionLevel,
    ): Boolean {
        if (available) presented += nudgeId to level
        return available
    }
}

class NudgeEvaluatorTest {
    private val eventDao = FakeEventDao()
    private val notifier = FakeNotifier()
    private val frictionPresenter = FakeFrictionPresenter()

    /** Draws handed out in order, holdout first then escalation; 0.5 once they run out. */
    private val draws = ArrayDeque<Double>()
    private var draw: Double
        get() = error("Write only")
        set(value) {
            draws.clear()
            draws += value
        }

    private val evaluator =
        NudgeEvaluator(
            eventDao = eventDao,
            watchedApps = { it == FEED },
            notifier = notifier,
            frictionPresenter = frictionPresenter,
            holdoutDraw = { draws.removeFirstOrNull() ?: 0.5 },
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

    /** Two snoozed notifications in the current session, so the next follow-up asks for an overlay. */
    private suspend fun twoSnoozesAgo() {
        eventDao.insert(foreground(sessionStart))
        evaluator.evaluate(now = sessionStart + 21 * MINUTE_MS, zone = PARIS)
        val first = notifier.shown.last().first
        eventDao.insert(responseEvent(first, FEED, NudgeResponse.Snooze, sessionStart + 22 * MINUTE_MS))
        evaluator.evaluate(now = sessionStart + 27 * MINUTE_MS, zone = PARIS)
        val second = notifier.shown.last().first
        eventDao.insert(responseEvent(second, FEED, NudgeResponse.Snooze, sessionStart + 28 * MINUTE_MS))
    }

    private fun lastDecision() = NudgeJson.decodeFromString<NudgeDecisionMetadata>(decisions().last().metadata)

    @Test
    fun `presents an overlay once the ladder asks for one`() =
        runTest {
            twoSnoozesAgo()

            evaluator.evaluate(now = sessionStart + 33 * MINUTE_MS, zone = PARIS)

            assertEquals(FrictionLevel.Overlay, frictionPresenter.presented.single().second)
            assertEquals(2, notifier.shown.size)
            assertEquals(1, lastDecision().frictionLevel)
            assertEquals(EVENT_TYPE_NUDGE_SHOWN, decisions().last().eventType)
        }

    @Test
    fun `falls back to a notification when the overlay cannot be shown`() =
        runTest {
            twoSnoozesAgo()
            frictionPresenter.available = false

            evaluator.evaluate(now = sessionStart + 33 * MINUTE_MS, zone = PARIS)

            assertEquals(3, notifier.shown.size)
            val metadata = lastDecision()
            assertEquals(0, metadata.frictionLevel)
            assertEquals(1, metadata.requestedFrictionLevel)
            assertEquals("service_unavailable", metadata.frictionFallback)
        }

    @Test
    fun `an overlay does not need notifications`() =
        runTest {
            twoSnoozesAgo()
            notifier.enabled = false

            evaluator.evaluate(now = sessionStart + 33 * MINUTE_MS, zone = PARIS)

            assertEquals(EVENT_TYPE_NUDGE_SHOWN, decisions().last().eventType)
            assertEquals(1, frictionPresenter.presented.size)
        }

    @Test
    fun `a held-out escalation stays a notification`() =
        runTest {
            twoSnoozesAgo()
            draws += listOf(0.5, 0.9)

            evaluator.evaluate(now = sessionStart + 33 * MINUTE_MS, zone = PARIS)

            assertEquals(emptyList<Pair<String, FrictionLevel>>(), frictionPresenter.presented)
            assertEquals(0, lastDecision().frictionLevel)
            assertEquals(1, lastDecision().requestedFrictionLevel)
        }
}
