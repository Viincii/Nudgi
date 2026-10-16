package com.vincentmignot.nudgi.core.bandit

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

private const val MINUTE = 60_000L
private const val T = 1_000_000_000L

class RewardV1Test {
    @Test
    fun `benefit is the share of a 30-minute day window spent off watched apps`() {
        val watched =
            listOf(
                UsageInterval(T - 5 * MINUTE, T + 6 * MINUTE),
                UsageInterval(
                    T + 20 * MINUTE,
                    T + 50 * MINUTE,
                ),
            )

        assertEquals(1 - 16.0 / 30, RewardV1.benefit(T, localHour = 14, watched = watched), 1e-9)
    }

    @Test
    fun `the night window is 60 minutes`() {
        val watched = listOf(UsageInterval(T + 40 * MINUTE, T + 55 * MINUTE))

        assertEquals(1 - 15.0 / 60, RewardV1.benefit(T, localHour = 1, watched = watched), 1e-9)
        assertEquals(1.0, RewardV1.benefit(T, localHour = 23, watched = watched), 1e-9)
    }

    @Test
    fun `overlapping intervals are counted once`() {
        val watched = listOf(UsageInterval(T, T + 10 * MINUTE), UsageInterval(T + 5 * MINUTE, T + 15 * MINUTE))

        assertEquals(0.5, RewardV1.benefit(T, localHour = 14, watched = watched), 1e-9)
    }

    @Test
    fun `night counts double, and every action pays its cost`() {
        assertEquals(0.5, RewardV1.reward(BanditAction.Nothing, localHour = 14, benefit = 0.5), 1e-9)
        assertEquals(0.8, RewardV1.reward(BanditAction.Overlay, localHour = 0, benefit = 0.5), 1e-9)
        assertEquals(0.5, RewardV1.reward(BanditAction.ForcedClose, localHour = 5, benefit = 0.5), 1e-9)
        assertEquals(1.0, RewardV1.weight(localHour = 6), 1e-9)
    }

    @Test
    fun `the sorted usage counts the same time as going through every interval`() {
        val random = Random(7)
        // Mostly short pieces, a few sessions of hours that start long before the windows they reach.
        val intervals =
            List(400) {
                val start = T + random.nextLong(0, 3 * 24 * 60) * MINUTE
                val length = if (random.nextInt(20) == 0) random.nextLong(60, 300) else random.nextLong(1, 30)
                UsageInterval(start, start + length * MINUTE)
            }
        val usage = WatchedUsage(intervals)

        repeat(500) {
            val from = T + random.nextLong(-60, 3 * 24 * 60 + 60) * MINUTE
            val until = from + listOf(30L, 60L).random(random) * MINUTE
            assertEquals(coveredMs(intervals, from, until), usage.usedMs(from, until))
        }
    }

    /** The time in `[from, until)` covered by [intervals], minute by minute. */
    private fun coveredMs(
        intervals: List<UsageInterval>,
        from: Long,
        until: Long,
    ): Long =
        (from until until step MINUTE).count { minute -> intervals.any { minute >= it.start && minute < it.end } } *
            MINUTE
}
