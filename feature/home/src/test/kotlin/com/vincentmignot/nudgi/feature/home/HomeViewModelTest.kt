package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.mascot.MascotMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeDailyStatsDao : DailyStatsDao {
    val rows = MutableStateFlow<List<DailyStatsEntity>>(emptyList())

    override suspend fun upsert(stats: DailyStatsEntity) = upsertAll(listOf(stats))

    override suspend fun upsertAll(stats: List<DailyStatsEntity>) {
        val keys = stats.map { it.date to it.packageName }.toSet()
        rows.value = rows.value.filterNot { (it.date to it.packageName) in keys } + stats
    }

    override fun observeForDate(date: String): Flow<List<DailyStatsEntity>> =
        rows.map { all -> all.filter { it.date == date } }

    override fun observeRecentForPackage(
        packageName: String,
        limit: Int,
    ): Flow<List<DailyStatsEntity>> = error("Not used by the home screen")
}

private class FakeEventDao : EventDao {
    val events = MutableStateFlow<List<EventEntity>>(emptyList())

    override suspend fun insert(event: EventEntity): Long {
        events.value += event
        return events.value.size.toLong()
    }

    override suspend fun insertAll(events: List<EventEntity>) {
        this.events.value += events
    }

    override fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>> = error("Not used by the home screen")

    override fun observeOfTypesBetween(
        eventTypes: List<String>,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>> =
        events.map { all ->
            all.filter { it.eventType in eventTypes && it.timestamp >= startInclusive && it.timestamp < endExclusive }
        }

    override suspend fun since(sinceInclusive: Long): List<EventEntity> = error("Not used by the home screen")

    override suspend fun ofTypeSince(
        eventType: String,
        sinceInclusive: Long,
    ): List<EventEntity> = error("Not used by the home screen")

    override suspend fun latest(limit: Int): List<EventEntity> = error("Not used by the home screen")
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dailyStatsDao = FakeDailyStatsDao()
    private val eventDao = FakeEventDao()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(startAt: Long): HomeViewModel {
        val viewModel =
            HomeViewModel(
                dailyStatsDao = dailyStatsDao,
                eventDao = eventDao,
                watchedApps = { it == FEED },
                appLabels = { it },
                clock = { startAt + currentTime },
                zone = PARIS,
                workDispatcher = dispatcher,
            )
        // WhileSubscribed only reads the database while someone collects the state.
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `nothing is shown before today's data is read`() {
        val viewModel =
            HomeViewModel(dailyStatsDao, eventDao, { true }, { it }, { 0L }, PARIS, dispatcher)

        assertFalse(viewModel.uiState.value.isLoaded)
    }

    @Test
    fun `follows today's stats and nudges as they are written`() =
        runTest(dispatcher) {
            val viewModel = viewModel(startAt = at("2026-09-24T12:00:00"))
            assertTrue(viewModel.uiState.value.isLoaded)
            assertEquals(MascotMood.Happy, viewModel.uiState.value.mood)

            dailyStatsDao.upsert(stats("2026-09-23", FEED, 300))
            dailyStatsDao.upsert(stats("2026-09-24", FEED, 45))
            eventDao.insert(shown("n1", at("2026-09-23T22:00:00")))
            eventDao.insert(shown("n2", at("2026-09-24T11:00:00")))
            eventDao.insert(outcome("n2", at("2026-09-24T11:11:00"), leftApp = false))

            val state = viewModel.uiState.value
            assertEquals(45 * MINUTE_MS, state.watchedUsageMs)
            assertEquals(listOf("n2"), state.nudges.map { it.nudgeId })
            assertEquals(MascotMood.Neutral, state.mood)
        }

    @Test
    fun `switches to the new day at midnight`() =
        runTest(dispatcher) {
            dailyStatsDao.upsert(stats("2026-09-24", FEED, 150))
            eventDao.insert(shown("n1", at("2026-09-24T23:30:00")))
            val viewModel = viewModel(startAt = at("2026-09-24T23:59:00"))
            assertEquals(MascotMood.Worried, viewModel.uiState.value.mood)

            advanceTimeBy(2 * MINUTE_MS)

            val state = viewModel.uiState.value
            assertEquals(0L, state.watchedUsageMs)
            assertEquals(emptyList<TodayNudge>(), state.nudges)
            assertEquals(MascotMood.Happy, state.mood)
        }

    @Test
    fun `a debug override replaces the mood until it is cleared`() =
        runTest(dispatcher) {
            val viewModel = viewModel(startAt = at("2026-09-24T12:00:00"))

            viewModel.onMoodSelected(MascotMood.Worried)
            assertEquals(MascotMood.Worried, viewModel.uiState.value.mood)
            assertTrue(viewModel.uiState.value.isMoodOverridden)

            viewModel.onMoodSelected(null)
            assertEquals(MascotMood.Happy, viewModel.uiState.value.mood)
            assertFalse(viewModel.uiState.value.isMoodOverridden)
        }
}
