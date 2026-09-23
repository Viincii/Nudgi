package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

private class FakeDailyStatsDao : DailyStatsDao {
    val rows = mutableMapOf<Pair<String, String>, DailyStatsEntity>()

    override suspend fun upsert(stats: DailyStatsEntity) {
        rows[stats.date to stats.packageName] = stats
    }

    override suspend fun upsertAll(stats: List<DailyStatsEntity>) {
        stats.forEach { upsert(it) }
    }

    override fun observeForDate(date: String): Flow<List<DailyStatsEntity>> =
        flowOf(rows.values.filter { it.date == date })

    override fun observeRecentForPackage(
        packageName: String,
        limit: Int,
    ): Flow<List<DailyStatsEntity>> =
        flowOf(
            rows.values
                .filter { it.packageName == packageName }
                .sortedByDescending { it.date }
                .take(limit),
        )
}

class DailyStatsAggregatorTest {
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val eventDao = FakeEventDao()
    private val dailyStatsDao = FakeDailyStatsDao()
    private val aggregator = DailyStatsAggregator(eventDao, dailyStatsDao)

    private fun at(dateTime: String): Long =
        LocalDateTime
            .parse(dateTime)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private suspend fun session(
        packageName: String,
        start: String,
        end: String,
    ) {
        eventDao.insert(
            EventEntity(
                timestamp = at(end),
                eventType = EVENT_TYPE_APP_BACKGROUND,
                packageName = packageName,
                durationMs = at(end) - at(start),
                metadata = "{}",
            ),
        )
    }

    private suspend fun nudgeShown(
        packageName: String,
        at: String,
    ) {
        eventDao.insert(
            EventEntity(
                timestamp = at(at),
                eventType = EVENT_TYPE_NUDGE_SHOWN,
                packageName = packageName,
                durationMs = 0,
                metadata = "{}",
            ),
        )
    }

    private fun usage(
        date: String,
        packageName: String,
    ): Long? = dailyStatsDao.rows[date to packageName]?.usageMs

    @Test
    fun `recomputes the whole day, not only the sessions since the given instant`() =
        runTest {
            session("com.example.app", start = "2026-09-22T09:00:00", end = "2026-09-22T09:10:00")
            session("com.example.app", start = "2026-09-22T14:00:00", end = "2026-09-22T14:05:00")

            aggregator.aggregateSince(at("2026-09-22T14:00:00"), zone)

            assertEquals(15 * 60_000L, usage("2026-09-22", "com.example.app"))
        }

    @Test
    fun `recomputes the previous day when a new session started before midnight`() =
        runTest {
            session("com.example.app", start = "2026-09-22T20:00:00", end = "2026-09-22T20:30:00")
            session("com.example.app", start = "2026-09-22T23:50:00", end = "2026-09-23T00:10:00")

            aggregator.aggregateSince(at("2026-09-23T00:00:00"), zone)

            assertEquals(40 * 60_000L, usage("2026-09-22", "com.example.app"))
            assertEquals(10 * 60_000L, usage("2026-09-23", "com.example.app"))
        }

    @Test
    fun `leaves days before the first touched day alone`() =
        runTest {
            dailyStatsDao.upsert(DailyStatsEntity("2026-09-21", "com.example.app", usageMs = 123L, nudgeCount = 0))
            session("com.example.app", start = "2026-09-21T10:00:00", end = "2026-09-21T11:00:00")
            session("com.example.app", start = "2026-09-22T10:00:00", end = "2026-09-22T10:01:00")

            aggregator.aggregateSince(at("2026-09-22T00:00:00"), zone)

            assertEquals(123L, usage("2026-09-21", "com.example.app"))
            assertEquals(60_000L, usage("2026-09-22", "com.example.app"))
        }

    @Test
    fun `is idempotent`() =
        runTest {
            session("com.example.app", start = "2026-09-22T09:00:00", end = "2026-09-22T09:10:00")

            aggregator.aggregateSince(at("2026-09-22T00:00:00"), zone)
            aggregator.aggregateSince(at("2026-09-22T00:00:00"), zone)

            assertEquals(10 * 60_000L, usage("2026-09-22", "com.example.app"))
        }

    @Test
    fun `writes nothing when no session closed since the given instant`() =
        runTest {
            session("com.example.app", start = "2026-09-22T09:00:00", end = "2026-09-22T09:10:00")

            aggregator.aggregateSince(at("2026-09-22T12:00:00"), zone)

            assertEquals(emptyMap<Pair<String, String>, DailyStatsEntity>(), dailyStatsDao.rows)
        }

    @Test
    fun `counts the nudges shown per day and app`() =
        runTest {
            session("com.example.app", start = "2026-09-22T09:00:00", end = "2026-09-22T09:30:00")
            nudgeShown("com.example.app", at = "2026-09-22T09:20:00")
            nudgeShown("com.example.app", at = "2026-09-22T09:25:00")

            aggregator.aggregateSince(at("2026-09-22T09:00:00"), zone)

            assertEquals(2, dailyStatsDao.rows.getValue("2026-09-22" to "com.example.app").nudgeCount)
        }

    @Test
    fun `a nudge alone is enough to recompute its day`() =
        runTest {
            session("com.example.app", start = "2026-09-22T09:00:00", end = "2026-09-22T09:30:00")
            nudgeShown("com.example.app", at = "2026-09-22T11:00:00")

            aggregator.aggregateSince(at("2026-09-22T10:00:00"), zone)

            val row = dailyStatsDao.rows.getValue("2026-09-22" to "com.example.app")
            assertEquals(30 * 60_000L, row.usageMs)
            assertEquals(1, row.nudgeCount)
        }
}
