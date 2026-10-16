package com.vincentmignot.nudgi.core.nudge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The `events.metadata` JSON of each nudge event type. Field names are snake_case to match the
// column naming, and they are the contract the on-device model will be trained on: rename with care.

/**
 * The policy that took every decision recorded from this build: the rules of [NudgeConfig]. Bump it
 * whenever the way decisions are taken changes, so the data of each policy can be told apart. Rows
 * recorded before the field existed have none; they were taken by `rules_v1`, the rules without
 * friction. `rules_v2` added friction, `rules_v3` a 50% holdout and no daily cap, `rules_v4` a
 * random coach expression, `rules_v5` a 20% holdout and the exploration of the friction levels.
 */
const val RULES_POLICY_ID = "rules_v5"

/** Metadata of `nudge_shown` and `nudge_suppressed`. */
@Serializable
data class NudgeDecisionMetadata(
    @SerialName("nudge_id") val nudgeId: String,
    /** Null on rows recorded before policies were identified; see [RULES_POLICY_ID]. */
    @SerialName("policy_id") val policyId: String? = null,
    @SerialName("rule_id") val ruleId: String,
    val level: Int,
    @SerialName("threshold_ms") val thresholdMs: Long,
    @SerialName("follow_up_of") val followUpOf: String? = null,
    /** Only for `nudge_suppressed`. */
    val reason: String? = null,
    /** Logged on every decision so the propensity of each action can be recovered offline. */
    @SerialName("holdout_probability") val holdoutProbability: Double,
    val context: NudgeContextSnapshot,
    /** What the shadow bandit would have done; null before it existed, or when it failed. */
    val shadow: ShadowMetadata? = null,
    // The friction fields are null on rows recorded before friction existed, which were all
    // notifications. See FrictionDecision.
    @SerialName("friction_level") val frictionLevel: Int? = null,
    @SerialName("requested_friction_level") val requestedFrictionLevel: Int? = null,
    /** Logged on every decision, like [holdoutProbability]; it only applied when the level requested was higher. */
    @SerialName("escalation_probability") val escalationProbability: Double? = null,
    @SerialName("friction_paused") val frictionPaused: Boolean? = null,
    @SerialName("friction_fallback") val frictionFallback: String? = null,
    /** Nudgi's expression in the intervention; null when nothing was shown, or on a forced close. */
    val expression: String? = null,
    @SerialName("expression_probability") val expressionProbability: Double? = null,
    /**
     * The probability of every action under the rules for this decision, by bandit action id, after
     * the holdout, the escalation, the exploration and any pause; actions they could not take are
     * left out. Null on rows recorded before `rules_v5`, whose propensities are recomputed from
     * [holdoutProbability] and [escalationProbability].
     */
    @SerialName("action_probabilities") val actionProbabilities: Map<String, Double>? = null,
    /** What the exploration did to the level; null when it left it alone. See FrictionExploration. */
    val explored: String? = null,
    /** The level the ladder counts this nudge at, when the exploration moved it; else [frictionLevel]. */
    @SerialName("ladder_friction_level") val ladderFrictionLevel: Int? = null,
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
    // Null on rows recorded before the shadow bandit, which derives them from the events instead.
    @SerialName("snoozes_today") val snoozesToday: Int? = null,
    @SerialName("friction_level_reached") val frictionLevelReached: Int? = null,
)

/** The shadow bandit's choice for a decision (decision 0020); it never acts on it. */
@Serializable
data class ShadowMetadata(
    @SerialName("policy_id") val policyId: String,
    val action: String,
    val propensity: Double,
    @SerialName("trained_on") val trainedOn: Int,
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
    /**
     * Whether the user came back to the app within [windowMs], after leaving it. A forced close
     * always leaves the app, so this is what tells whether it changed anything. Null on rows
     * recorded before it was measured.
     */
    val reopened: Boolean? = null,
    @SerialName("reopened_after_ms") val reopenedAfterMs: Long? = null,
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
