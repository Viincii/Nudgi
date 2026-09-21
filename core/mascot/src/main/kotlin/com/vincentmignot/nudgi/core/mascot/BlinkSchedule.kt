package com.vincentmignot.nudgi.core.mascot

import kotlin.random.Random

internal const val FIRST_BLINK_DELAY_MS = 2_100L
internal const val BLINK_HALF_DURATION_MS = 130
internal const val BLINK_CLOSED_SCALE = 0.08f
private const val MIN_BLINK_INTERVAL_MS = 2_800L
private const val MAX_BLINK_INTERVAL_MS = 5_000L

/** Blinks at irregular intervals, which reads as alive where a fixed rhythm reads as mechanical. */
internal fun nextBlinkDelayMillis(random: Random): Long =
    random.nextLong(MIN_BLINK_INTERVAL_MS, MAX_BLINK_INTERVAL_MS + 1)
