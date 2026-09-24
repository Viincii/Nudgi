package com.vincentmignot.nudgi.core.nudge

/**
 * Told by [NudgeActionReceiver] once the user's answer to a nudge is recorded in `events`, so
 * whatever schedules the next evaluation can take it into account. Called on a background thread.
 */
fun interface NudgeResponseListener {
    fun onNudgeResponse(
        packageName: String,
        response: NudgeResponse,
    )
}
