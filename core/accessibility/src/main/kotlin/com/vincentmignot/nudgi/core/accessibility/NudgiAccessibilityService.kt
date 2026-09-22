package com.vincentmignot.nudgi.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Placeholder so the onboarding flow can request and verify the accessibility permission ahead of
 * the app-blocking work (roadmap step 4), which is what will give this service real event handling.
 */
class NudgiAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
