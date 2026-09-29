package com.vincentmignot.nudgi.core.bandit

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
