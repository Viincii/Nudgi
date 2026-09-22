package com.vincentmignot.nudgi.core.usagestats

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface UsagePollState {
    /** End of the last successfully-polled window, in epoch millis, or null before the first poll. */
    fun lastPolledUntil(): Long?

    fun setLastPolledUntil(timestamp: Long)
}

class SharedPreferencesUsagePollState
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : UsagePollState {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun lastPolledUntil(): Long? = prefs.getLong(KEY_LAST_POLLED_UNTIL, NOT_SET).takeIf { it != NOT_SET }

        override fun setLastPolledUntil(timestamp: Long) {
            prefs.edit { putLong(KEY_LAST_POLLED_UNTIL, timestamp) }
        }

        private companion object {
            const val PREFS_NAME = "usage_poll_state"
            const val KEY_LAST_POLLED_UNTIL = "last_polled_until"
            const val NOT_SET = -1L
        }
    }
