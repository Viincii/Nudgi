package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EventDao
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/**
 * How far back events are read: a full local day, a late-night window that started the evening
 * before, and a session that started before either of them.
 */
private const val HISTORY_MS = 30 * 60 * 60 * 1000L

/** A uniform draw in `[0, 1)` deciding holdouts and escalations; injected so tests can pin it. */
fun interface HoldoutDraw {
    fun next(): Double
}

class RandomHoldoutDraw
    @Inject
    constructor() : HoldoutDraw {
        override fun next(): Double = Random.nextDouble()
    }

/**
 * Runs the rules against the events recorded so far: records the outcome of past nudges whose
 * window has closed, then the decision for the app in the foreground, and shows the nudge if that
 * decision is to show one, as a notification or with the friction the app has reached.
 *
 * Friction is applied before the decision is recorded, so a nudge whose overlay could not be shown
 * is recorded as the notification it fell back to.
 */
class NudgeEvaluator
    @Inject
    constructor(
        private val eventDao: EventDao,
        private val watchedApps: WatchedApps,
        private val notifier: NudgeNotifier,
        private val frictionPresenter: FrictionPresenter,
        private val shadowPolicy: ShadowPolicy,
        private val holdoutDraw: HoldoutDraw,
        private val config: NudgeConfig,
    ) {
        /**
         * Returns when the rules should next be evaluated if the user stays in the current app, or
         * null when no watched app is in the foreground or nothing more can fire today.
         */
        suspend fun evaluate(
            now: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): Long? {
            val events = eventDao.since(now - HISTORY_MS)

            val outcomes = pendingOutcomeEvents(events, now, config)
            if (outcomes.isNotEmpty()) eventDao.insertAll(outcomes)

            val context = buildNudgeContext(events, now, zone, watchedApps::isWatched, config) ?: return null
            var decision = decideNudge(context, config, holdoutDraw.next())
            val candidate = decision.candidate ?: return nextEvaluationAt(context, config, zone)
            var friction = decideFriction(context, candidate, config, holdoutDraw.next())

            val nudgeId = UUID.randomUUID().toString()
            if (decision is NudgeDecision.Show && friction.applied != FrictionLevel.Notification) {
                val presented = frictionPresenter.present(nudgeId, candidate, context, friction.applied)
                if (!presented) friction = friction.fallingBack(FrictionFallback.ServiceUnavailable)
            }
            val notifies = decision is NudgeDecision.Show && friction.applied == FrictionLevel.Notification
            if (notifies && !notifier.canNotify()) {
                decision = NudgeDecision.Suppress(candidate, SuppressionReason.NotificationsDisabled)
            }

            val shadow = shadowChoice(context, candidate, zone)
            eventDao.insert(decisionEvent(decision, friction, context, config, nudgeId, shadow))
            if (decision is NudgeDecision.Show && notifies) notifier.show(nudgeId, candidate, context)
            return nextEvaluationAt(context.including(decision, friction, nudgeId), config, zone)
        }

        /** The shadow bandit never acts, so its failure is recorded as a missing shadow, never allowed to stop a nudge. */
        private suspend fun shadowChoice(
            context: NudgeContext,
            candidate: NudgeCandidate,
            zone: ZoneId,
        ): ShadowMetadata? =
            try {
                shadowPolicy.choose(context, candidate, zone)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }

/** [this] context as it will look once [decision] is recorded, for scheduling what comes next. */
private fun NudgeContext.including(
    decision: NudgeDecision,
    friction: FrictionDecision,
    nudgeId: String,
): NudgeContext {
    val (candidate, shown) =
        when (decision) {
            is NudgeDecision.Show -> {
                decision.candidate to true
            }

            is NudgeDecision.Suppress -> {
                if (decision.reason == SuppressionReason.Holdout) decision.candidate to false else return this
            }

            NudgeDecision.None -> {
                return this
            }
        }
    val decided =
        PastNudge(nudgeId, now, packageName, candidate.rule, candidate.level, shown, frictionLevel = friction.applied)
    return copy(pastNudges = pastNudges + decided)
}
