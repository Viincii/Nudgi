package com.vincentmignot.nudgi.core.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Records the user's answer to a nudge notification: a button tap or swiping it away. */
@AndroidEntryPoint
class NudgeActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var recorder: NudgeResponseRecorder

    @Inject
    lateinit var listener: NudgeResponseListener

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val nudgeId = intent.getStringExtra(EXTRA_NUDGE_ID) ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val response = NudgeResponse.fromId(intent.getStringExtra(EXTRA_RESPONSE)) ?: return

        // Action buttons do not dismiss the notification on their own; cancelling does not fire
        // the delete intent, so this is not recorded as a dismissal too.
        NotificationManagerCompat.from(context).cancel(notificationIdFor(nudgeId))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recorder.record(nudgeId, packageName, response)
                listener.onNudgeResponse(packageName, response)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_NUDGE_ID = "nudge_id"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_RESPONSE = "response"
    }
}
