package com.vincentmignot.nudgi.core.accessibility

/** Told by [NudgiAccessibilityService] each time an app comes to the foreground. */
fun interface ForegroundAppListener {
    fun onForegroundApp(packageName: String)
}
