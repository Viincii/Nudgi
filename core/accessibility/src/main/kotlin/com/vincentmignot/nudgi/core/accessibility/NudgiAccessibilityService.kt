package com.vincentmignot.nudgi.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

/**
 * Reports which app is in the foreground, from window state changes. It does not read screen
 * content. The notification shade and the keyboard open windows of their own on top of an app, so
 * they are not reported: they do not end the session of the app underneath.
 *
 * It also carries out friction through [AccessibilityActions]: drawing an overlay over an app,
 * which needs no permission beyond this service, and sending the user home.
 */
@AndroidEntryPoint
class NudgiAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var listener: ForegroundAppListener

    private class ShownOverlay(
        val packageName: String,
        val view: View,
        val onRemoved: () -> Unit,
    )

    private val overlays = mutableListOf<ShownOverlay>()

    override fun onServiceConnected() {
        running = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == SYSTEM_UI_PACKAGE || packageName == defaultInputMethodPackage()) return
        // An overlay is a window of Nudgi's own; it covers the app, it does not replace it.
        if (packageName == this.packageName && overlays.isNotEmpty()) return
        overlays.filter { it.packageName != packageName }.forEach(::remove)
        listener.onForegroundApp(packageName)
    }

    override fun onInterrupt() = Unit

    internal fun showOverlay(
        packageName: String,
        createView: (Context) -> View,
        onRemoved: () -> Unit,
    ): Overlay {
        val view = createView(this)
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Not focusable: back, home and recents keep working, so the user is never trapped.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        getSystemService(WindowManager::class.java).addView(view, params)
        val shown = ShownOverlay(packageName, view, onRemoved)
        overlays += shown
        return Overlay { remove(shown) }
    }

    internal fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    private fun remove(overlay: ShownOverlay) {
        if (!overlays.remove(overlay)) return
        getSystemService(WindowManager::class.java).removeView(overlay.view)
        overlay.onRemoved()
    }

    private fun stop() {
        if (running === this) running = null
        overlays.toList().forEach(::remove)
    }

    private fun defaultInputMethodPackage(): String? =
        Settings.Secure
            .getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }

    internal companion object {
        /** The connected instance, if any. Only touched on the main thread, like the service itself. */
        var running: NudgiAccessibilityService? = null
            private set
    }
}
