package com.vincentmignot.nudgi.core.today

import com.vincentmignot.nudgi.core.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayMoodTest {
    @Test
    fun `a light day with no ignored nudge is happy`() {
        assertEquals(MascotMood.Happy, moodFor(watchedUsageMs = NEUTRAL_USAGE_MS - 1, nudgesKeptGoing = 0))
    }

    @Test
    fun `an hour on watched apps turns neutral`() {
        assertEquals(MascotMood.Neutral, moodFor(watchedUsageMs = NEUTRAL_USAGE_MS, nudgesKeptGoing = 0))
    }

    @Test
    fun `one nudge the user kept going after turns neutral`() {
        assertEquals(MascotMood.Neutral, moodFor(watchedUsageMs = 0, nudgesKeptGoing = 1))
    }

    @Test
    fun `two hours on watched apps is worrying`() {
        assertEquals(MascotMood.Worried, moodFor(watchedUsageMs = WORRIED_USAGE_MS, nudgesKeptGoing = 0))
    }

    @Test
    fun `repeatedly ignored nudges are worrying`() {
        assertEquals(MascotMood.Worried, moodFor(watchedUsageMs = 0, nudgesKeptGoing = WORRIED_NUDGES_KEPT_GOING))
    }
}
