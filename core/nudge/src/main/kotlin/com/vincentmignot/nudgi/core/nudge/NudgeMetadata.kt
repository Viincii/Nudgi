package com.vincentmignot.nudgi.core.nudge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The `events.metadata` JSON of each nudge event type. Field names are snake_case to match the
// column naming, and they are the contract the on-device model will be trained on: rename with care.

/** Metadata of `nudge_shown` and `nudge_suppressed`. */
@Serializable
data class NudgeDecisionMetadata(
    @SerialName("nudge_id") val nudgeId: String,
    @SerialName("rule_id") val ruleId: String,
    val level: Int,
    @SerialName("threshold_ms") val thresholdMs: Long,
    @SerialName("follow_up_of") val followUpOf: String? = null,
    /** Only for `nudge_suppressed`. */
    val reason: String? = null,
    /** Logged on every decision so the propensity of each action can be recovered offline. */
    @SerialName("holdout_probability") val holdoutProbability: Double,
    val context: NudgeContextSnapshot,
)

@Serializable
data class NudgeContextSnapshot(
    @SerialName("session_ms") val sessionMs: Long,
    @SerialName("daily_ms") val dailyMs: Long,
    @SerialName("late_night_ms") val lateNightMs: Long,
    @SerialName("local_hour") val localHour: Int,
    val weekday: Int,
    @SerialName("nudges_today") val nudgesToday: Int,
    @SerialName("ms_since_last_nudge") val msSinceLastNudge: Long? = null,
)

/** Metadata of `nudge_response`. */
@Serializable
data class NudgeResponseMetadata(
    @SerialName("nudge_id") val nudgeId: String,
    val response: String,
)

/** Metadata of `nudge_outcome`: whether the user left the app within [windowMs] of the decision. */
@Serializable
data class NudgeOutcomeMetadata(
    @SerialName("nudge_id") val nudgeId: String,
    @SerialName("left_app") val leftApp: Boolean,
    @SerialName("left_after_ms") val leftAfterMs: Long? = null,
    @SerialName("window_ms") val windowMs: Long,
)

internal val NudgeJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

internal inline fun <reified T> decodeOrNull(metadata: String): T? =
    try {
        NudgeJson.decodeFromString<T>(metadata)
    } catch (_: IllegalArgumentException) {
        null
    }
