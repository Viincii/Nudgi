package com.vincentmignot.nudgi.core.nudge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NudgeNotifier {
    fun canNotify(): Boolean

    fun show(
        nudgeId: String,
        candidate: NudgeCandidate,
        nudgeContext: NudgeContext,
    )
}

private const val CHANNEL_ID = "nudges"
private const val MINUTE_MS = 60_000L

/** Whether nudges can reach the user: the runtime permission on Android 13+, and not blocked in settings. */
fun areNudgeNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun notificationIdFor(nudgeId: String): Int = nudgeId.hashCode()

class AndroidNudgeNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NudgeNotifier {
        override fun canNotify(): Boolean = areNudgeNotificationsEnabled(context)

        override fun show(
            nudgeId: String,
            candidate: NudgeCandidate,
            nudgeContext: NudgeContext,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannel()
            val packageName = nudgeContext.packageName
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_nudge_notification)
                    .setContentTitle(context.getString(R.string.nudge_title))
                    .setContentText(message(candidate, nudgeContext))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .addAction(
                        0,
                        context.getString(R.string.nudge_action_stop),
                        responseIntent(nudgeId, packageName, NudgeResponse.Stop),
                    ).addAction(
                        0,
                        context.getString(R.string.nudge_action_snooze),
                        responseIntent(nudgeId, packageName, NudgeResponse.Snooze),
                    ).setDeleteIntent(responseIntent(nudgeId, packageName, NudgeResponse.Dismissed))
                    .build()
            NotificationManagerCompat.from(context).notify(notificationIdFor(nudgeId), notification)
        }

        private fun message(
            candidate: NudgeCandidate,
            nudgeContext: NudgeContext,
        ): String {
            val appLabel = appLabel(nudgeContext.packageName)
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

        private fun appLabel(packageName: String): String {
            val packageManager = context.packageManager
            return try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }

        private fun responseIntent(
            nudgeId: String,
            packageName: String,
            response: NudgeResponse,
        ): PendingIntent {
            val intent =
                Intent(context, NudgeActionReceiver::class.java)
                    .putExtra(NudgeActionReceiver.EXTRA_NUDGE_ID, nudgeId)
                    .putExtra(NudgeActionReceiver.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(NudgeActionReceiver.EXTRA_RESPONSE, response.id)
            return PendingIntent.getBroadcast(
                context,
                31 * notificationIdFor(nudgeId) + response.ordinal,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.nudge_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.nudge_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
