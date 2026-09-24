package com.vincentmignot.nudgi.core.today

import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_OUTCOME
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_RESPONSE
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.nudge.nudgeOutcomes
import com.vincentmignot.nudgi.core.nudge.pastNudges
import com.vincentmignot.nudgi.core.usagestats.localDateOf
import com.vincentmignot.nudgi.core.usagestats.startMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The event types [todayOf] reads; suppressions are never shown to the user. */
internal val TODAY_EVENT_TYPES = listOf(EVENT_TYPE_NUDGE_SHOWN, EVENT_TYPE_NUDGE_RESPONSE, EVENT_TYPE_NUDGE_OUTCOME)

/** Builds [Today] from the day's `daily_stats` rows and its [TODAY_EVENT_TYPES] events. */
internal fun todayOf(
    stats: List<DailyStatsEntity>,
    events: List<EventEntity>,
    isWatched: (String) -> Boolean,
    labelOf: (String) -> String,
    zone: ZoneId,
): Today {
    val watchedUsageMs = stats.filter { isWatched(it.packageName) }.sumOf { it.usageMs }
    val outcomes = nudgeOutcomes(events)
    val nudges =
        pastNudges(events)
            .filter { it.shown }
            .map { nudge ->
                TodayNudge(
                    nudgeId = nudge.nudgeId,
                    time = Instant.ofEpochMilli(nudge.timestamp).atZone(zone).toLocalTime(),
                    appLabel = labelOf(nudge.packageName),
                    rule = nudge.rule,
                    outcome =
                        when (outcomes[nudge.nudgeId]) {
                            true -> TodayNudge.Outcome.TookABreak
                            false -> TodayNudge.Outcome.KeptGoing
                            null -> TodayNudge.Outcome.Pending
                        },
                )
            }
    return Today(watchedUsageMs = watchedUsageMs, nudges = nudges)
}

/** Today's local date, then the next one at each midnight, for as long as it is collected. */
internal fun localDates(
    clock: () -> Long,
    zone: ZoneId,
): Flow<LocalDate> =
    flow {
        while (true) {
            val now = clock()
            val today = localDateOf(now, zone)
            emit(today)
            delay(today.plusDays(1).startMillis(zone) - now)
        }
    }
