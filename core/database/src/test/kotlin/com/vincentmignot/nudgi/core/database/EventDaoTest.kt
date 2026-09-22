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
}
