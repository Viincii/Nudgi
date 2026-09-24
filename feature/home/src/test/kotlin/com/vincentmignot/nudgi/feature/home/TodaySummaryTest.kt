package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.mascot.MascotMood
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TodaySummaryTest {
    private val isWatched: (String) -> Boolean = { it == FEED || it == VIDEO }
    private val labelOf: (String) -> String = { it.substringAfterLast('.').replaceFirstChar(Char::uppercase) }

    private fun state(
        stats: List<DailyStatsEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
    ) = todayUiState(stats, events, isWatched, labelOf, PARIS)

    @Test
    fun `only watched apps count towards usage`() {
        val state =
            state(
                stats =
                    listOf(
                        stats("2026-09-24", FEED, 30),
                        stats("2026-09-24", VIDEO, 40),
                        stats("2026-09-24", LAUNCHER, 300),
                    ),
            )

        assertEquals(70 * MINUTE_MS, state.watchedUsageMs)
        assertEquals(MascotMood.Neutral, state.mood)
        assertTrue(state.isLoaded)
    }

    @Test
    fun `lists shown nudges with their outcome and leaves held-out ones out`() {
        val events =
            listOf(
                shown("n1", at("2026-09-24T09:42:00"), packageName = VIDEO),
                heldOut("n2", at("2026-09-24T10:00:00")),
                shown("n3", at("2026-09-24T13:05:00")),
                shown("n4", at("2026-09-24T18:30:00")),
                outcome("n1", at("2026-09-24T09:53:00"), leftApp = true, packageName = VIDEO),
                outcome("n2", at("2026-09-24T10:11:00"), leftApp = false),
                outcome("n3", at("2026-09-24T13:16:00"), leftApp = false),
            )

        val nudges = state(events = events).nudges

        assertEquals(listOf("n1", "n3", "n4"), nudges.map { it.nudgeId })
        assertEquals(LocalTime.of(9, 42), nudges[0].time)
        assertEquals("Video", nudges[0].appLabel)
        assertEquals(
            listOf(TodayNudge.Outcome.TookABreak, TodayNudge.Outcome.KeptGoing, TodayNudge.Outcome.Pending),
            nudges.map { it.outcome },
        )
    }

    @Test
    fun `only nudges the user kept going after weigh on the mood`() {
        val events =
            listOf(
                shown("n1", at("2026-09-24T09:00:00")),
                shown("n2", at("2026-09-24T10:00:00")),
                shown("n3", at("2026-09-24T11:00:00")),
                outcome("n1", at("2026-09-24T09:11:00"), leftApp = true),
                outcome("n2", at("2026-09-24T10:11:00"), leftApp = false),
            )

        assertEquals(MascotMood.Neutral, state(events = events).mood)
        assertEquals(
            MascotMood.Worried,
            state(events = events + outcome("n3", at("2026-09-24T11:11:00"), leftApp = false)).mood,
        )
    }

    @Test
    fun `local dates move on at each midnight`() =
        runTest {
            val start = at("2026-09-24T23:58:00")

            val dates = localDates({ start + currentTime }, PARIS).take(3).toList()

            assertEquals(
                listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)),
                dates,
            )
            assertEquals(at("2026-09-26T00:00:00") - start, currentTime)
        }
}
