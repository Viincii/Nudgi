package com.vincentmignot.nudgi.core.accessibility

import android.content.Context
import android.view.View
import javax.inject.Inject
import javax.inject.Singleton

/** A window drawn over another app by [AccessibilityActions.showOverlay]. */
fun interface Overlay {
    fun remove()
}

/**
 * What [NudgiAccessibilityService] can do beyond reporting foreground changes. Everything here
 * needs the service to be running, and must be called on the main thread.
 */
interface AccessibilityActions {
    /**
     * Draws a full-screen window over [packageName] with the view from [createView], which gets
     * the service's context. The window is removed on its own as soon as another app comes to the
     * foreground, e.g. when the user goes home, and [onRemoved] is called whichever way it goes.
     * Returns null when the service is not running.
     */
    fun showOverlay(
        packageName: String,
        createView: (Context) -> View,
        onRemoved: () -> Unit,
    ): Overlay?

    /** Sends the user to the home screen; false when the service is not running. */
    fun goHome(): Boolean
}

@Singleton
class ServiceAccessibilityActions
    @Inject
    constructor() : AccessibilityActions {
        override fun showOverlay(
            packageName: String,
            createView: (Context) -> View,
            onRemoved: () -> Unit,
        ): Overlay? = NudgiAccessibilityService.running?.showOverlay(packageName, createView, onRemoved)

        override fun goHome(): Boolean = NudgiAccessibilityService.running?.goHome() == true
    }
