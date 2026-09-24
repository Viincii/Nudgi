package com.vincentmignot.nudgi.core.today

import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.nudge.NudgeRule
import java.time.LocalTime

/** What Nudgi knows about the current day, shared by every surface that shows it. */
data class Today(
    /** Time on watched apps, from sessions already closed. */
    val watchedUsageMs: Long,
    /** Nudges shown today, oldest first. Held-out nudges were never seen and are left out. */
    val nudges: List<TodayNudge>,
) {
    val mood: MascotMood
        get() = moodFor(watchedUsageMs, nudges.count { it.outcome == TodayNudge.Outcome.KeptGoing })
}

data class TodayNudge(
    val nudgeId: String,
    val time: LocalTime,
    val appLabel: String,
    val rule: NudgeRule,
    val outcome: Outcome,
) {
    enum class Outcome {
        /** The user left the app within the outcome window. */
        TookABreak,

        /** The user was still in the app when the outcome window closed. */
        KeptGoing,

        /** The outcome window has not closed yet, or no evaluation has recorded it. */
        Pending,
    }
}
