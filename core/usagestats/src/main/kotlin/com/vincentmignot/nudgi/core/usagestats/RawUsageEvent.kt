package com.vincentmignot.nudgi.core.usagestats

enum class UsageEventType {
    Foreground,
    Background,
}

/**
 * A foreground/background transition for one app, decoupled from the Android `UsageEvents.Event`
 * type so the mapping to `EventEntity` can be unit-tested on the JVM.
 */
data class RawUsageEvent(
    val timestamp: Long,
    val packageName: String,
    val type: UsageEventType,
)
