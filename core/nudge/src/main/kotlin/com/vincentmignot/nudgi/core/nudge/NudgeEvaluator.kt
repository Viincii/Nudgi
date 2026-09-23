package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EventDao
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/**
 * How far back events are read: a full local day, a late-night window that started the evening
 * before, and a session that started before either of them.
 */
private const val HISTORY_MS = 30 * 60 * 60 * 1000L

/** A uniform draw in `[0, 1)` deciding holdouts; injected so tests can pin it. */
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
 * decision is to show one.
 */
class NudgeEvaluator
    @Inject
    constructor(
        private val eventDao: EventDao,
        private val watchedApps: WatchedApps,
        private val notifier: NudgeNotifier,
        private val holdoutDraw: HoldoutDraw,
        private val config: NudgeConfig,
    ) {
        suspend fun evaluate(
            now: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ) {
            val events = eventDao.since(now - HISTORY_MS)

            val outcomes = pendingOutcomeEvents(events, now, config)
            if (outcomes.isNotEmpty()) eventDao.insertAll(outcomes)

            val context = buildNudgeContext(events, now, zone, watchedApps::isWatched, config) ?: return
            var decision = decideNudge(context, config, holdoutDraw.next())
            if (decision is NudgeDecision.Show && !notifier.canNotify()) {
                decision = NudgeDecision.Suppress(decision.candidate, SuppressionReason.NotificationsDisabled)
            }
            if (decision == NudgeDecision.None) return

            val nudgeId = UUID.randomUUID().toString()
            eventDao.insert(decisionEvent(decision, context, config, nudgeId))
            if (decision is NudgeDecision.Show) notifier.show(nudgeId, decision.candidate, context)
        }
    }
