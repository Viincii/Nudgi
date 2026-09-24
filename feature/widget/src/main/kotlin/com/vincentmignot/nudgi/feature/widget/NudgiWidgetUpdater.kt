package com.vincentmignot.nudgi.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Redraws every placed Nudgi widget from the current day; does nothing when none is placed. */
class NudgiWidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun update() {
            NudgiWidget().updateAll(context)
        }
    }
