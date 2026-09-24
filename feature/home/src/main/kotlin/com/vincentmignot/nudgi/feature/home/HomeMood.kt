package com.vincentmignot.nudgi.feature.home

import com.vincentmignot.nudgi.core.mascot.MascotMood

private const val MINUTE_MS = 60_000L

/** Watched-app usage today from which Nudgi stops looking happy, then starts looking worried. */
internal const val NEUTRAL_USAGE_MS = 60 * MINUTE_MS
internal const val WORRIED_USAGE_MS = 120 * MINUTE_MS

/** Nudges today after which the user stayed in the app, from which Nudgi starts looking worried. */
internal const val WORRIED_NUDGES_KEPT_GOING = 2

/**
 * How Nudgi feels about today. A nudge only weighs on the mood once its outcome shows the user kept
 * going: one they acted on is a good sign, and one still in its observation window is not known yet.
 */
internal fun moodFor(
    watchedUsageMs: Long,
    nudgesKeptGoing: Int,
): MascotMood =
    when {
        watchedUsageMs >= WORRIED_USAGE_MS || nudgesKeptGoing >= WORRIED_NUDGES_KEPT_GOING -> MascotMood.Worried
        watchedUsageMs >= NEUTRAL_USAGE_MS || nudgesKeptGoing >= 1 -> MascotMood.Neutral
        else -> MascotMood.Happy
    }
