package com.vincentmignot.nudgi.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

/**
 * Reports which app is in the foreground, from window state changes. It does not read screen
 * content. The notification shade and the keyboard open windows of their own on top of an app, so
 * they are not reported: they do not end the session of the app underneath.
 */
@AndroidEntryPoint
class NudgiAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var listener: ForegroundAppListener

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == SYSTEM_UI_PACKAGE || packageName == defaultInputMethodPackage()) return
        listener.onForegroundApp(packageName)
    }

    override fun onInterrupt() = Unit

    private fun defaultInputMethodPackage(): String? =
        Settings.Secure
            .getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
}
