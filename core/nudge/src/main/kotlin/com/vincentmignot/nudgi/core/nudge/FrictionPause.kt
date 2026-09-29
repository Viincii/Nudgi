package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EVENT_TYPE_FRICTION_PAUSED
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The "pause friction" of Settings: while it lasts, every nudge is a notification again. */
interface FrictionPause {
    /** End of the pause while one is running, null otherwise; turns null on its own once it ends. */
    val pausedUntil: Flow<Long?>

    suspend fun pause()
}

/**
 * Records a pause as an event, like every other intervention, so the data shows when friction was
 * paused rather than a user who suddenly stopped getting overlays.
 */
class EventFrictionPause
    @Inject
    constructor(
        private val eventDao: EventDao,
        private val config: NudgeConfig,
    ) : FrictionPause {
        @OptIn(ExperimentalCoroutinesApi::class)
        override val pausedUntil: Flow<Long?>
            get() {
                // Only a pause started in the last pause length can still be running.
                val since = System.currentTimeMillis() - config.frictionPauseMs
                return eventDao
                    .observeOfTypesBetween(listOf(EVENT_TYPE_FRICTION_PAUSED), since, Long.MAX_VALUE)
                    .map(::frictionPausedUntil)
                    .distinctUntilChanged()
                    .flatMapLatest { until ->
                        flow {
                            val remainingMs = until?.let { it - System.currentTimeMillis() } ?: 0L
                            if (remainingMs <= 0L) {
                                emit(null)
                            } else {
                                emit(until)
                                delay(remainingMs)
                                emit(null)
                            }
                        }
                    }
            }

        override suspend fun pause() {
            eventDao.insert(
                EventEntity(
                    timestamp = System.currentTimeMillis(),
                    eventType = EVENT_TYPE_FRICTION_PAUSED,
                    packageName = null,
                    durationMs = config.frictionPauseMs,
                    metadata = "{}",
                ),
            )
        }
    }
