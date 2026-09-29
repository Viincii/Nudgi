package com.vincentmignot.nudgi.core.nudge

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val MINUTE_MS = 60_000L

/** The text of a nudge, shared by the notification and the friction overlay. */
class NudgeMessages
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appLabels: AppLabels,
    ) {
        fun title(): String = context.getString(R.string.nudge_title)

        fun message(
            candidate: NudgeCandidate,
            nudgeContext: NudgeContext,
        ): String {
            val appLabel = appLabels.labelOf(nudgeContext.packageName)
            return when (candidate.rule) {
                NudgeRule.LongSession -> {
                    context.getString(
                        R.string.nudge_long_session,
                        appLabel,
                        (nudgeContext.sessionMs / MINUTE_MS).toInt(),
                    )
                }

                NudgeRule.DailyBudget -> {
                    context.getString(
                        R.string.nudge_daily_budget,
                        appLabel,
                        (nudgeContext.dailyUsageMs / MINUTE_MS).toInt(),
                    )
                }

                NudgeRule.LateNight -> {
                    context.getString(R.string.nudge_late_night, appLabel)
                }

                NudgeRule.SnoozeFollowUp -> {
                    context.getString(R.string.nudge_snooze_followup, appLabel)
                }
            }
        }
    }
