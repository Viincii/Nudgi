package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_FOREGROUND
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageEventMapperTest {
    @Test
    fun `foreground event maps with zero duration`() {
        val raw = RawUsageEvent(timestamp = 1_000, packageName = "com.example.app", type = UsageEventType.Foreground)

        val entities = mapToEventEntities(listOf(raw))

        assertEquals(1, entities.size)
        assertEquals(EVENT_TYPE_APP_FOREGROUND, entities[0].eventType)
        assertEquals(0L, entities[0].durationMs)
        assertEquals("com.example.app", entities[0].packageName)
    }

    @Test
    fun `background event paired with a prior foreground reports the session length`() {
        val raw =
            listOf(
                RawUsageEvent(timestamp = 1_000, packageName = "com.example.app", type = UsageEventType.Foreground),
                RawUsageEvent(timestamp = 5_500, packageName = "com.example.app", type = UsageEventType.Background),
            )

        val entities = mapToEventEntities(raw)

        assertEquals(EVENT_TYPE_APP_BACKGROUND, entities[1].eventType)
        assertEquals(4_500L, entities[1].durationMs)
    }

    @Test
    fun `background event with no matching foreground reports zero duration`() {
        val raw = RawUsageEvent(timestamp = 1_000, packageName = "com.example.app", type = UsageEventType.Background)

        val entities = mapToEventEntities(listOf(raw))

        assertEquals(0L, entities[0].durationMs)
    }

    @Test
    fun `sessions for different packages do not interfere with each other`() {
        val raw =
            listOf(
                RawUsageEvent(timestamp = 1_000, packageName = "com.example.a", type = UsageEventType.Foreground),
                RawUsageEvent(timestamp = 2_000, packageName = "com.example.b", type = UsageEventType.Foreground),
                RawUsageEvent(timestamp = 3_000, packageName = "com.example.a", type = UsageEventType.Background),
                RawUsageEvent(timestamp = 6_000, packageName = "com.example.b", type = UsageEventType.Background),
            )

        val entities = mapToEventEntities(raw)

        assertEquals(2_000L, entities[2].durationMs)
        assertEquals(4_000L, entities[3].durationMs)
    }
}
