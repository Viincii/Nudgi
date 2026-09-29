package com.vincentmignot.nudgi.feature.friction

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import com.vincentmignot.nudgi.core.accessibility.AccessibilityActions
import com.vincentmignot.nudgi.core.accessibility.Overlay
import com.vincentmignot.nudgi.core.designsystem.NudgiTheme
import com.vincentmignot.nudgi.core.nudge.AppLabels
import com.vincentmignot.nudgi.core.nudge.FrictionLevel
import com.vincentmignot.nudgi.core.nudge.FrictionPresenter
import com.vincentmignot.nudgi.core.nudge.NudgeCandidate
import com.vincentmignot.nudgi.core.nudge.NudgeConfig
import com.vincentmignot.nudgi.core.nudge.NudgeContext
import com.vincentmignot.nudgi.core.nudge.NudgeMessages
import com.vincentmignot.nudgi.core.nudge.NudgeResponse
import com.vincentmignot.nudgi.core.nudge.NudgeResponseListener
import com.vincentmignot.nudgi.core.nudge.NudgeResponseRecorder
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies friction through the accessibility service: an overlay over the app for levels 1 and 2,
 * the home screen for level 3.
 *
 * The overlay's buttons record the same responses as the notification's, so a snooze on an
 * overlay brings its follow-up and counts toward the next level exactly like one in the shade.
 * Leaving the app instead removes the overlay without a response; the outcome records it.
 *
 * [responseListener] is lazy because what listens to responses also runs the pipeline that ends
 * up here.
 */
@Singleton
class OverlayFrictionPresenter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val accessibility: AccessibilityActions,
        private val messages: NudgeMessages,
        private val appLabels: AppLabels,
        private val config: NudgeConfig,
        private val recorder: NudgeResponseRecorder,
        private val responseListener: Lazy<NudgeResponseListener>,
    ) : FrictionPresenter {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** The overlay on screen, if any. Only touched on the main thread. */
        private var current: Overlay? = null

        override suspend fun present(
            nudgeId: String,
            candidate: NudgeCandidate,
            nudgeContext: NudgeContext,
            level: FrictionLevel,
        ): Boolean =
            withContext(Dispatchers.Main.immediate) {
                current?.remove()
                when (level) {
                    FrictionLevel.Notification -> {
                        error("A notification is not friction")
                    }

                    FrictionLevel.Overlay, FrictionLevel.CountdownOverlay -> {
                        showOverlay(
                            nudgeId,
                            candidate,
                            nudgeContext,
                            level,
                        )
                    }

                    FrictionLevel.ForcedClose -> {
                        closeApp(nudgeContext.packageName)
                    }
                }
            }

        private fun showOverlay(
            nudgeId: String,
            candidate: NudgeCandidate,
            nudgeContext: NudgeContext,
            level: FrictionLevel,
        ): Boolean {
            val packageName = nudgeContext.packageName
            return showOverlay(
                packageName = packageName,
                message = messages.message(candidate, nudgeContext),
                level = level,
                onStop = { respond(nudgeId, packageName, NudgeResponse.Stop) },
                onSnooze = { respond(nudgeId, packageName, NudgeResponse.Snooze) },
            )
        }

        /**
         * Applies [level] over [packageName] without any nudge behind it: nothing is recorded, and
         * the buttons only remove the overlay. For checking friction by hand on a debug build.
         */
        internal fun preview(
            packageName: String,
            level: FrictionLevel,
        ): Boolean =
            when (level) {
                FrictionLevel.Notification -> {
                    false
                }

                FrictionLevel.ForcedClose -> {
                    closeApp(packageName)
                }

                FrictionLevel.Overlay, FrictionLevel.CountdownOverlay -> {
                    current?.remove()
                    val dismiss = { current?.remove() ?: Unit }
                    showOverlay(packageName, "Preview of the friction overlay.", level, dismiss, dismiss)
                }
            }

        private fun showOverlay(
            packageName: String,
            message: String,
            level: FrictionLevel,
            onStop: () -> Unit,
            onSnooze: () -> Unit,
        ): Boolean {
            val title = messages.title()
            val countdownMs = if (level == FrictionLevel.CountdownOverlay) config.frictionCountdownMs else 0L
            val lifecycleOwner = OverlayLifecycleOwner()
            var overlay: Overlay? = null
            overlay =
                accessibility.showOverlay(
                    packageName = packageName,
                    createView = { serviceContext ->
                        ComposeView(serviceContext).apply {
                            lifecycleOwner.attachTo(this)
                            setContent {
                                NudgiTheme {
                                    FrictionOverlay(
                                        title = title,
                                        message = message,
                                        countdownMs = countdownMs,
                                        onStop = onStop,
                                        onSnooze = onSnooze,
                                    )
                                }
                            }
                        }
                    },
                    onRemoved = {
                        lifecycleOwner.destroy()
                        if (current === overlay) current = null
                    },
                )
            current = overlay
            return overlay != null
        }

        private fun closeApp(packageName: String): Boolean {
            if (!accessibility.goHome()) return false
            Toast
                .makeText(
                    context,
                    context.getString(R.string.friction_closed, appLabels.labelOf(packageName)),
                    Toast.LENGTH_LONG,
                ).show()
            return true
        }

        private fun respond(
            nudgeId: String,
            packageName: String,
            response: NudgeResponse,
        ) {
            current?.remove()
            scope.launch {
                recorder.record(nudgeId, packageName, response)
                responseListener.get().onNudgeResponse(packageName, response)
            }
        }
    }
