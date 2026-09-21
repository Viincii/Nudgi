package com.vincentmignot.nudgi.core.mascot

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlinkScheduleTest {
    @Test
    fun `blink delays stay within the natural interval`() {
        val random = Random(seed = 42)

        repeat(1_000) {
            val delay = nextBlinkDelayMillis(random)

            assertTrue("delay $delay", delay in 2_800L..5_000L)
        }
    }

    @Test
    fun `blink delays are irregular`() {
        val random = Random(seed = 42)

        val distinct = List(20) { nextBlinkDelayMillis(random) }.toSet()

        assertTrue(distinct.size > 10)
    }
}
