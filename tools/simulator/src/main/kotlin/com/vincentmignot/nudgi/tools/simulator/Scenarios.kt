package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.BanditContext
import com.vincentmignot.nudgi.core.bandit.RewardV1
import kotlin.math.ln

private const val MINUTE_MS = 60_000.0

/** Below this many decisions in a cell, a stop probability is not fitted but assumed. */
private const val MIN_FITTED = 5

/**
 * Stop probabilities fitted on an export, for doing nothing and for a notification, by day and by
 * night: the only actions with enough data (decision 0022). Every other level is assumed.
 */
data class Calibration(
    val nothingDay: Fitted,
    val nothingNight: Fitted,
    val notificationDay: Fitted,
    val notificationNight: Fitted,
) {
    data class Fitted(
        val stopProbability: Double,
        /** Decisions it was fitted on; 0 when it is the default. */
        val count: Int,
    )

    fun nothing(night: Boolean) = if (night) nothingNight else nothingDay

    fun notification(night: Boolean) = if (night) notificationNight else notificationDay

    companion object {
        /** Assumed when there is no export, or too little data in a cell. */
        val DEFAULT =
            Calibration(
                nothingDay = Fitted(0.20, 0),
                nothingNight = Fitted(0.15, 0),
                notificationDay = Fitted(0.30, 0),
                notificationNight = Fitted(0.30, 0),
            )

        /**
         * The stop probability whose expected benefit matches the mean benefit observed: the
         * expected benefit is linear in it, between carrying on (0) and stopping (1).
         */
        fun fit(
            decisions: List<ExportedDecision>,
            aftermath: Aftermath,
        ): Calibration {
            fun cell(
                action: BanditAction,
                night: Boolean,
                default: Fitted,
            ): Fitted {
                val cell =
                    decisions.filter {
                        it.action == action && it.benefit != null && it.decision.context.isNight == night
                    }
                if (cell.size < MIN_FITTED) return default
                val benefits = cell.mapNotNull { it.benefit }
                // Every decision of a cell has the same window, since it depends on day or night only.
                val window =
                    RewardV1.windowMs(
                        cell
                            .first()
                            .decision.context.localHour,
                    ) / MINUTE_MS
                val carriedOn = aftermath.benefitIfCarriedOn(window)
                val stopped = aftermath.benefitIfStopped(window)
                val p = (benefits.average() - carriedOn) / (stopped - carriedOn)
                return Fitted(p.coerceIn(0.02, 0.98), benefits.size)
            }
            return Calibration(
                nothingDay = cell(BanditAction.Nothing, night = false, DEFAULT.nothingDay),
                nothingNight = cell(BanditAction.Nothing, night = true, DEFAULT.nothingNight),
                notificationDay = cell(BanditAction.Notification, night = false, DEFAULT.notificationDay),
                notificationNight = cell(BanditAction.Notification, night = true, DEFAULT.notificationNight),
            )
        }
    }
}

/** The worlds of decision 0022, each with a known best action in every context. */
enum class Scenario(
    val id: String,
    val description: String,
) {
    Linear(
        "linear",
        "stop probability linear in the bandit's own features: the model is right, so it must converge",
    ),
    CalibratedLow(
        "calibrated-low",
        "nothing and notification fitted on the export; every higher level no better than a notification",
    ),
    CalibratedHigh(
        "calibrated-high",
        "nothing and notification fitted on the export; each higher level clearly better, forced close nearly always",
    ),
    NothingWorks(
        "nothing-works",
        "every action as good as doing nothing: the costs must stop the bandit from nudging",
    ),
    ;

    fun user(
        calibration: Calibration,
        aftermath: Aftermath,
    ): SimulatedUser =
        when (this) {
            Linear -> {
                SimulatedUser(id, aftermath) { context, action ->
                    val s = ln(1 + context.sessionMs / MINUTE_MS)
                    val n = if (RewardV1.isNight(context.localHour)) 1.0 else 0.0
                    when (action) {
                        BanditAction.Nothing -> 0.15 + 0.03 * s
                        BanditAction.Notification -> 0.20 + 0.03 * s + 0.30 * n
                        BanditAction.Overlay -> 0.15 + 0.07 * s + 0.20 * n
                        BanditAction.CountdownOverlay -> 0.15 + 0.07 * s + 0.15 * n
                        BanditAction.ForcedClose -> 0.60
                    }
                }
            }

            CalibratedLow -> {
                SimulatedUser(id, aftermath) { context, action ->
                    val night = context.isNight
                    when (action) {
                        BanditAction.Nothing -> calibration.nothing(night).stopProbability
                        else -> calibration.notification(night).stopProbability
                    }
                }
            }

            CalibratedHigh -> {
                SimulatedUser(id, aftermath) { context, action ->
                    val night = context.isNight
                    val notification = calibration.notification(night).stopProbability
                    when (action) {
                        BanditAction.Nothing -> calibration.nothing(night).stopProbability
                        BanditAction.Notification -> notification
                        BanditAction.Overlay -> notification + 0.10
                        BanditAction.CountdownOverlay -> notification + 0.20
                        BanditAction.ForcedClose -> 0.95
                    }
                }
            }

            NothingWorks -> {
                SimulatedUser(id, aftermath) { context, _ -> calibration.nothing(context.isNight).stopProbability }
            }
        }

    companion object {
        fun fromId(id: String): Scenario? = entries.firstOrNull { it.id == id }
    }
}

private val BanditContext.isNight: Boolean get() = RewardV1.isNight(localHour)
