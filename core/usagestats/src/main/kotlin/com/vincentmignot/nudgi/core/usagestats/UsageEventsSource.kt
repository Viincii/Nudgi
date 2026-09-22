package com.vincentmignot.nudgi.core.usagestats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface UsageEventsSource {
    fun queryEvents(
        startTime: Long,
        endTime: Long,
    ): List<RawUsageEvent>
}

class SystemUsageEventsSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UsageEventsSource {
        override fun queryEvents(
            startTime: Long,
            endTime: Long,
        ): List<RawUsageEvent> {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val result = mutableListOf<RawUsageEvent>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type =
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.Foreground
                        UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.Background
                        else -> null
                    } ?: continue
                result.add(RawUsageEvent(timestamp = event.timeStamp, packageName = event.packageName, type = type))
            }
            return result
        }
    }
