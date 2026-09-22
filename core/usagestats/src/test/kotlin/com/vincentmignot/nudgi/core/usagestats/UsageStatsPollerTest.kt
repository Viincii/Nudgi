package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeEventDao : EventDao {
    val inserted = mutableListOf<EventEntity>()

    override suspend fun insert(event: EventEntity): Long {
        inserted += event
        return inserted.size.toLong()
    }

    override suspend fun insertAll(events: List<EventEntity>) {
        inserted += events
    }

    override fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>> = flowOf(inserted)

    override suspend fun latest(limit: Int): List<EventEntity> = inserted.takeLast(limit)
}

private class FakePollState(
    private var lastPolledUntil: Long? = null,
) : UsagePollState {
    override fun lastPolledUntil(): Long? = lastPolledUntil

    override fun setLastPolledUntil(timestamp: Long) {
        lastPolledUntil = timestamp
    }
}

class UsageStatsPollerTest {
    private val eventDao = FakeEventDao()

    private fun poller(
        rawEvents: List<RawUsageEvent> = emptyList(),
        pollState: FakePollState = FakePollState(),
        granted: Boolean = true,
    ) = UsageStatsPoller(
        eventsSource = UsageEventsSource { _, _ -> rawEvents },
        eventDao = eventDao,
        pollState = pollState,
        permissionChecker = UsageAccessPermissionChecker { granted },
    )

    @Test
    fun `does nothing without usage access`() =
        runTest {
            val pollState = FakePollState()

            poller(granted = false, pollState = pollState).poll(now = 10_000)

            assertEquals(emptyList<EventEntity>(), eventDao.inserted)
            assertNull(pollState.lastPolledUntil())
        }

    @Test
    fun `inserts mapped events and advances the poll window`() =
        runTest {
            val pollState = FakePollState(lastPolledUntil = 1_000)
            val rawEvents =
                listOf(
                    RawUsageEvent(timestamp = 2_000, packageName = "com.example.app", type = UsageEventType.Foreground),
                )

            poller(rawEvents = rawEvents, pollState = pollState).poll(now = 5_000)

            assertEquals(1, eventDao.inserted.size)
            assertEquals("com.example.app", eventDao.inserted[0].packageName)
            assertEquals(5_000L, pollState.lastPolledUntil())
        }

    @Test
    fun `first poll looks back a fixed window instead of querying since epoch`() =
        runTest {
            val pollState = FakePollState(lastPolledUntil = null)
            var queriedStart: Long? = null
            val poller =
                UsageStatsPoller(
                    eventsSource =
                        UsageEventsSource { start, _ ->
                            queriedStart = start
                            emptyList()
                        },
                    eventDao = eventDao,
                    pollState = pollState,
                    permissionChecker = UsageAccessPermissionChecker { true },
                )

            poller.poll(now = 20 * 60 * 1000L)

            assertEquals(5 * 60 * 1000L, queriedStart)
        }
}
