package com.vincentmignot.nudgi.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventDaoTest {
    private lateinit var database: NudgiDatabase
    private lateinit var dao: EventDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NudgiDatabase::class.java)
                .build()
        dao = database.eventDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun event(
        timestamp: Long,
        eventType: String,
        packageName: String? = "com.example.app",
    ) = EventEntity(
        timestamp = timestamp,
        eventType = eventType,
        packageName = packageName,
        durationMs = 0L,
        metadata = "{}",
    )

    @Test
    fun `insert then latest returns the event`() =
        runTest {
            dao.insert(event(timestamp = 1_000L, eventType = "app_foregrounded"))

            val latest = dao.latest(limit = 10)

            assertEquals(1, latest.size)
            assertEquals("app_foregrounded", latest.first().eventType)
        }

    @Test
    fun `observeBetween only returns events within range`() =
        runTest {
            dao.insert(event(timestamp = 500L, eventType = "a"))
            dao.insert(event(timestamp = 1_500L, eventType = "b"))
            dao.insert(event(timestamp = 2_500L, eventType = "c"))

            val inRange = dao.observeBetween(startInclusive = 1_000L, endExclusive = 2_000L).first()

            assertEquals(listOf("b"), inRange.map { it.eventType })
        }

    @Test
    fun `latest orders events most recent first`() =
        runTest {
            dao.insert(event(timestamp = 1_000L, eventType = "first"))
            dao.insert(event(timestamp = 2_000L, eventType = "second"))

            val latest = dao.latest(limit = 10)

            assertEquals(listOf("second", "first"), latest.map { it.eventType })
        }

    @Test
    fun `ofTypeSince filters by type and lower bound, oldest first`() =
        runTest {
            dao.insert(event(timestamp = 500L, eventType = "app_background"))
            dao.insert(event(timestamp = 2_000L, eventType = "app_background"))
            dao.insert(event(timestamp = 1_500L, eventType = "app_foreground"))
            dao.insert(event(timestamp = 1_000L, eventType = "app_background"))

            val events = dao.ofTypeSince(eventType = "app_background", sinceInclusive = 1_000L)

            assertEquals(listOf(1_000L, 2_000L), events.map { it.timestamp })
        }

    @Test
    fun `observeOfTypesBetween filters by types and half-open range, oldest first`() =
        runTest {
            dao.insert(event(timestamp = 1_500L, eventType = "nudge_response"))
            dao.insert(event(timestamp = 1_000L, eventType = "nudge_shown"))
            dao.insert(event(timestamp = 1_200L, eventType = "app_foreground"))
            dao.insert(event(timestamp = 999L, eventType = "nudge_shown"))
            dao.insert(event(timestamp = 2_000L, eventType = "nudge_shown"))

            val events =
                dao
                    .observeOfTypesBetween(
                        eventTypes = listOf("nudge_shown", "nudge_response"),
                        startInclusive = 1_000L,
                        endExclusive = 2_000L,
                    ).first()

            assertEquals(listOf(1_000L, 1_500L), events.map { it.timestamp })
        }

    @Test
    fun `maxId is null on an empty table`() =
        runTest {
            assertEquals(null, dao.maxId())
        }

    @Test
    fun `pageByIdAfter pages in id order and stops at the upper bound`() =
        runTest {
            // Inserted out of timestamp order: a scan by id must not depend on timestamps.
            listOf(3_000L, 1_000L, 2_000L, 4_000L).forEach { dao.insert(event(timestamp = it, eventType = "e")) }
            val upToId = dao.maxId()!! - 1

            val first = dao.pageByIdAfter(afterId = 0L, upToId = upToId, limit = 2)
            val second = dao.pageByIdAfter(afterId = first.last().id, upToId = upToId, limit = 2)
            val third = dao.pageByIdAfter(afterId = second.last().id, upToId = upToId, limit = 2)

            assertEquals(listOf(3_000L, 1_000L), first.map { it.timestamp })
            assertEquals(listOf(2_000L), second.map { it.timestamp })
            assertEquals(emptyList<EventEntity>(), third)
        }
}
