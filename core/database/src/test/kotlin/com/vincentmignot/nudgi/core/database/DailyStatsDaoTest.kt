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
class DailyStatsDaoTest {
    private lateinit var database: NudgiDatabase
    private lateinit var dao: DailyStatsDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NudgiDatabase::class.java)
                .build()
        dao = database.dailyStatsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `upsert inserts a new row`() =
        runTest {
            dao.upsert(
                DailyStatsEntity(
                    date = "2026-09-22",
                    packageName = "com.example.app",
                    usageMs = 60_000L,
                    nudgeCount = 2,
                ),
            )

            val stats = dao.observeForDate("2026-09-22").first()

            assertEquals(1, stats.size)
            assertEquals(60_000L, stats.first().usageMs)
        }

    @Test
    fun `upsert replaces the row for the same date and package`() =
        runTest {
            val key =
                DailyStatsEntity(
                    date = "2026-09-22",
                    packageName = "com.example.app",
                    usageMs = 60_000L,
                    nudgeCount = 2,
                )
            dao.upsert(key)
            dao.upsert(key.copy(usageMs = 90_000L, nudgeCount = 3))

            val stats = dao.observeForDate("2026-09-22").first()

            assertEquals(1, stats.size)
            assertEquals(90_000L, stats.first().usageMs)
            assertEquals(3, stats.first().nudgeCount)
        }

    @Test
    fun `observeRecentForPackage filters by package and orders by date desc`() =
        runTest {
            dao.upsert(DailyStatsEntity("2026-09-20", "com.example.app", 10_000L, 1))
            dao.upsert(DailyStatsEntity("2026-09-21", "com.example.app", 20_000L, 2))
            dao.upsert(DailyStatsEntity("2026-09-21", "com.other.app", 5_000L, 0))

            val recent = dao.observeRecentForPackage(packageName = "com.example.app", limit = 10).first()

            assertEquals(listOf("2026-09-21", "2026-09-20"), recent.map { it.date })
        }
}
