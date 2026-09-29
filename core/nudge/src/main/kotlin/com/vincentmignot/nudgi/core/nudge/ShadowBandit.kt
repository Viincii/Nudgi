package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.BanditContext
import com.vincentmignot.nudgi.core.bandit.LinearThompsonSampling
import com.vincentmignot.nudgi.core.bandit.Observation
import com.vincentmignot.nudgi.core.bandit.RewardV1
import com.vincentmignot.nudgi.core.bandit.UsageInterval
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_APP_BACKGROUND
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_RESPONSE
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SHOWN
import com.vincentmignot.nudgi.core.database.EVENT_TYPE_NUDGE_SUPPRESSED
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.usagestats.localDateOf
import com.vincentmignot.nudgi.core.usagestats.sessionsFrom
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

/**
 * The shadow bandit's id: linear Thompson sampling on the features of `core:bandit`, trained on
 * [RewardV1]. Bump it whenever either changes.
 */
const val SHADOW_POLICY_ID = "lin_ts_v1"

/** Chooses what the bandit would do for a decision, without acting on it. */
fun interface ShadowPolicy {
    suspend fun choose(
        context: NudgeContext,
        candidate: NudgeCandidate,
        zone: ZoneId,
    ): ShadowMetadata
}

/** Refits the bandit from the whole history at every decision (decision 0020). */
class LinearThompsonShadow internal constructor(
    private val eventDao: EventDao,
    private val watchedApps: WatchedApps,
    private val config: NudgeConfig,
    private val random: Random,
) : ShadowPolicy {
    @Inject
    constructor(eventDao: EventDao, watchedApps: WatchedApps, config: NudgeConfig) :
        this(eventDao, watchedApps, config, Random.Default)

    private val bandit = LinearThompsonSampling()

    override suspend fun choose(
        context: NudgeContext,
        candidate: NudgeCandidate,
        zone: ZoneId,
    ): ShadowMetadata {
        val observations = banditObservations(eventDao.since(0L), context.now, zone, watchedApps::isWatched, config)
        // The user asked to be reminded, so doing nothing is not on the table.
        val allowed =
            if (candidate.rule == NudgeRule.SnoozeFollowUp) {
                BanditAction.entries.toSet() - BanditAction.Nothing
            } else {
                BanditAction.entries.toSet()
            }
        val choice = bandit.choose(banditContext(context.snapshot(), candidate.rule.id), allowed, observations, random)
        return ShadowMetadata(SHADOW_POLICY_ID, choice.action.id, choice.propensity, choice.trainedOn)
    }
}

fun banditContext(
    snapshot: NudgeContextSnapshot,
    ruleId: String,
    derivedSnoozesToday: Int = 0,
): BanditContext =
    BanditContext(
        localHour = snapshot.localHour,
        weekday = snapshot.weekday,
        sessionMs = snapshot.sessionMs,
        dailyMs = snapshot.dailyMs,
        lateNightMs = snapshot.lateNightMs,
        nudgesToday = snapshot.nudgesToday,
        msSinceLastNudge = snapshot.msSinceLastNudge,
        snoozesToday = snapshot.snoozesToday ?: derivedSnoozesToday,
        // Rows recorded before friction had none to reach.
        frictionLevelReached = snapshot.frictionLevelReached ?: 0,
        ruleId = ruleId,
    )

/**
 * What the bandit learns from in [events]: every decision whose reward window has closed by [now],
 * with the action actually taken and its benefit. A shown nudge took the friction level it was
 * applied at; a held-out one, and the first cooldown suppression of a rule, level and session, did
 * nothing. Suppressions for any other reason are left out: the daily cap no longer exists, and
 * disabled notifications say nothing about what a nudge does.
 */
fun banditObservations(
    events: List<EventEntity>,
    now: Long,
    zone: ZoneId,
    isWatched: (String) -> Boolean,
    config: NudgeConfig,
): List<Observation> {
    val watched = watchedIntervals(events, now, isWatched, config)
    val snoozes =
        events.filter { event ->
            event.eventType == EVENT_TYPE_NUDGE_RESPONSE &&
                decodeOrNull<NudgeResponseMetadata>(event.metadata)?.response == NudgeResponse.Snooze.id
        }
    val seenCooldowns = mutableSetOf<List<Any>>()

    return events.mapNotNull { event ->
        val shown =
            when (event.eventType) {
                EVENT_TYPE_NUDGE_SHOWN -> true
                EVENT_TYPE_NUDGE_SUPPRESSED -> false
                else -> return@mapNotNull null
            }
        val packageName = event.packageName ?: return@mapNotNull null
        val metadata = decodeOrNull<NudgeDecisionMetadata>(event.metadata) ?: return@mapNotNull null
        val snapshot = metadata.context
        if (now < event.timestamp + RewardV1.windowMs(snapshot.localHour) + config.sessionMergeGapMs) {
            return@mapNotNull null
        }
        val action =
            if (shown) {
                FrictionLevel.fromValue(metadata.frictionLevel)?.banditAction ?: BanditAction.Notification
            } else {
                when (SuppressionReason.fromId(metadata.reason)) {
                    SuppressionReason.Holdout -> {
                        BanditAction.Nothing
                    }

                    SuppressionReason.Cooldown -> {
                        val sessionStart = event.timestamp - snapshot.sessionMs
                        val key =
                            listOf(
                                metadata.ruleId,
                                metadata.level,
                                packageName,
                                sessionStart / config.sessionMergeGapMs,
                            )
                        if (!seenCooldowns.add(key)) return@mapNotNull null
                        BanditAction.Nothing
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }
            }
        val day = localDateOf(event.timestamp, zone)
        val derivedSnoozes =
            snoozes.count {
                it.packageName == packageName && it.timestamp < event.timestamp &&
                    localDateOf(it.timestamp, zone) == day
            }
        Observation(
            context = banditContext(snapshot, metadata.ruleId, derivedSnoozes),
            action = action,
            benefit = RewardV1.benefit(event.timestamp, snapshot.localHour, watched),
        )
    }
}

/** Watched-app use in [events], including a session still running at [now]. */
private fun watchedIntervals(
    events: List<EventEntity>,
    now: Long,
    isWatched: (String) -> Boolean,
    config: NudgeConfig,
): List<UsageInterval> {
    val closed =
        sessionsFrom(events.filter { it.eventType == EVENT_TYPE_APP_BACKGROUND })
            .filter { isWatched(it.packageName) }
            .map { UsageInterval(it.start, it.end) }
    val open =
        currentSession(events, config.sessionMergeGapMs)
            ?.takeIf { isWatched(it.packageName) }
            ?.let { UsageInterval(it.pieceStartedAt, now) }
    return closed + listOfNotNull(open)
}

internal val FrictionLevel.banditAction: BanditAction
    get() =
        when (this) {
            FrictionLevel.Notification -> BanditAction.Notification
            FrictionLevel.Overlay -> BanditAction.Overlay
            FrictionLevel.CountdownOverlay -> BanditAction.CountdownOverlay
            FrictionLevel.ForcedClose -> BanditAction.ForcedClose
        }
