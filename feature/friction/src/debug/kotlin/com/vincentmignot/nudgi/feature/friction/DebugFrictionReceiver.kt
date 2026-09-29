package com.vincentmignot.nudgi.feature.friction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vincentmignot.nudgi.core.nudge.FrictionLevel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "DebugFriction"

/**
 * Shows a friction level on demand, since reaching it for real takes a long session and several
 * snoozes, and UI automation cannot drive the screen without unbinding the accessibility service.
 * Nothing is recorded, so the usage data stays the user's own:
 *
 *     adb shell am broadcast -a com.vincentmignot.nudgi.DEBUG_FRICTION \
 *         --ei level 1 --es package com.instagram.android
 */
@AndroidEntryPoint
class DebugFrictionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var presenter: OverlayFrictionPresenter

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val level = FrictionLevel.fromValue(intent.getIntExtra("level", FrictionLevel.Overlay.value))
        val packageName = intent.getStringExtra("package")
        if (level == null || packageName == null) {
            Log.w(TAG, "Expected --ei level 1..3 and --es package <name>")
            return
        }
        Log.i(TAG, "Preview of $level over $packageName: ${presenter.preview(packageName, level)}")
    }
}
