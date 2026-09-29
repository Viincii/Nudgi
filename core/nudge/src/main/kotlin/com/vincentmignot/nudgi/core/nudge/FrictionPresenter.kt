package com.vincentmignot.nudgi.core.nudge

/** Applies the friction levels above a notification: an overlay over the app, or sending the user home. */
fun interface FrictionPresenter {
    /**
     * Applies [level] for the nudge [nudgeId]. Returns false when it could not, because the
     * accessibility service is not running, so the nudge can fall back to a notification.
     */
    suspend fun present(
        nudgeId: String,
        candidate: NudgeCandidate,
        nudgeContext: NudgeContext,
        level: FrictionLevel,
    ): Boolean
}
