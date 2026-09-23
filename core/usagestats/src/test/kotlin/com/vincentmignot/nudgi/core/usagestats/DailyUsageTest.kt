package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private const val APP = "com.example.app"
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

private fun at(dateTime: String): Long =
    LocalDateTime
        .parse(dateTime)
        .atZone(PARIS)
        .toInstant()
        .toEpochMilli()

private fun background(
    timestamp: Long,
    durationMs: Long,
    packageName: String? = APP,
    eventType: String = EVENT_TYPE_APP_BACKGROUND,
) = EventEntity(
    timestamp = timestamp,
    eventType = eventType,
    packageName = packageName,
    durationMs = durationMs,
    metadata = "{}",
)

class DailyUsageTest {
    @Test
    fun `sessionsFrom rebuilds the span ending at the background event`() {
        val end = at("2026-09-22T10:30:00")

        val sessions = sessionsFrom(listOf(background(timestamp = end, durationMs = 5 * MINUTE)))

        assertEquals(listOf(UsageSession(APP, end - 5 * MINUTE, end)), sessions)
    }

    @Test
    fun `sessionsFrom drops events without a duration, a package or the background type`() {
        val end = at("2026-09-22T10:30:00")

        val sessions =
            sessionsFrom(
                listOf(
                    background(timestamp = end, durationMs = 0),
                    background(timestamp = end, durationMs = MINUTE, packageName = null),
                    background(timestamp = end, durationMs = MINUTE, eventType = EVENT_TYPE_APP_FOREGROUND),
                ),
            )

        assertEquals(emptyList<UsageSession>(), sessions)
    }

    @Test
    fun `dailyUsage sums sessions per day and package`() {
        val sessions =
            listOf(
                UsageSession(APP, at("2026-09-22T10:00:00"), at("2026-09-22T10:10:00")),
                UsageSession(APP, at("2026-09-22T18:00:00"), at("2026-09-22T18:05:00")),
                UsageSession("com.other.app", at("2026-09-22T12:00:00"), at("2026-09-22T12:01:00")),
            )

        val usage = dailyUsage(sessions, PARIS)

        assertEquals(
            mapOf(
                DailyUsageKey(LocalDate.parse("2026-09-22"), APP) to 15 * MINUTE,
                DailyUsageKey(LocalDate.parse("2026-09-22"), "com.other.app") to MINUTE,
            ),
            usage,
        )
    }

    @Test
    fun `dailyUsage splits a session crossing midnight`() {
        val session = UsageSession(APP, at("2026-09-22T23:40:00"), at("2026-09-23T00:15:00"))

        val usage = dailyUsage(listOf(session), PARIS)

        assertEquals(
            mapOf(
                DailyUsageKey(LocalDate.parse("2026-09-22"), APP) to 20 * MINUTE,
                DailyUsageKey(LocalDate.parse("2026-09-23"), APP) to 15 * MINUTE,
            ),
            usage,
        )
    }

    @Test
    fun `dailyUsage counts a 23-hour day when clocks go forward`() {
        val session = UsageSession(APP, at("2026-03-29T00:00:00"), at("2026-03-30T00:00:00"))

        val usage = dailyUsage(listOf(session), PARIS)

        assertEquals(mapOf(DailyUsageKey(LocalDate.parse("2026-03-29"), APP) to 23 * HOUR), usage)
    }
}
