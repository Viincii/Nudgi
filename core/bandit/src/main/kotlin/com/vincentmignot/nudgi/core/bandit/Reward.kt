package com.vincentmignot.nudgi.core.bandit

private const val MINUTE_MS = 60_000L

/** Watched-app use between [start] and [end], in epoch milliseconds. */
data class UsageInterval(
    val start: Long,
    val end: Long,
)

/**
 * The reward of decision 0019: `weight × benefit − cost`, where the benefit is the share of the
 * window after a decision not spent on watched apps. The weight and the window depend on the local
 * hour of the decision only, never on the action. Every value here is a choice of the user's:
 * changing one means a new [ID], and rewards recomputed over the whole history.
 */
object RewardV1 {
    const val ID = "reward_v1"

    private const val NIGHT_START_HOUR = 0
    private const val NIGHT_END_HOUR = 6
    private const val NIGHT_WEIGHT = 2.0
    private const val DAY_WINDOW_MS = 30 * MINUTE_MS
    private const val NIGHT_WINDOW_MS = 60 * MINUTE_MS

    fun isNight(localHour: Int): Boolean = localHour in NIGHT_START_HOUR until NIGHT_END_HOUR

    fun weight(localHour: Int): Double = if (isNight(localHour)) NIGHT_WEIGHT else 1.0

    fun windowMs(localHour: Int): Long = if (isNight(localHour)) NIGHT_WINDOW_MS else DAY_WINDOW_MS

    fun cost(action: BanditAction): Double =
        when (action) {
            BanditAction.Nothing -> 0.0
            BanditAction.Notification -> 0.1
            BanditAction.Overlay -> 0.2
            BanditAction.CountdownOverlay -> 0.3
            BanditAction.ForcedClose -> 0.5
        }

    /**
     * Share of the window after [decidedAt] not spent on watched apps, in `[0, 1]`. Overlapping
     * [watched] intervals are counted once, so it never goes negative.
     */
    fun benefit(
        decidedAt: Long,
        localHour: Int,
        watched: List<UsageInterval>,
    ): Double {
        val windowEnd = decidedAt + windowMs(localHour)
        val clipped =
            watched
                .map { UsageInterval(maxOf(it.start, decidedAt), minOf(it.end, windowEnd)) }
                .filter { it.end > it.start }
                .sortedBy { it.start }
        var usedMs = 0L
        var coveredUntil = decidedAt
        for (interval in clipped) {
            val start = maxOf(interval.start, coveredUntil)
            if (interval.end > start) usedMs += interval.end - start
            coveredUntil = maxOf(coveredUntil, interval.end)
        }
        return 1.0 - usedMs.toDouble() / windowMs(localHour)
    }

    fun reward(
        action: BanditAction,
        localHour: Int,
        benefit: Double,
    ): Double = weight(localHour) * benefit - cost(action)
}
