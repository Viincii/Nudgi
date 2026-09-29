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
import com.vincentmignot.nudgi.core.mascot.renderMascot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NudgeNotifier {
    fun canNotify(): Boolean

    fun show(
        nudgeId: String,
        candidate: NudgeCandidate,
        nudgeContext: NudgeContext,
        expression: CoachExpression,
    )
}

private const val CHANNEL_ID = "nudges"

/** The size Android shows a notification's large icon at. */
private const val LARGE_ICON_DP = 64

/** Whether nudges can reach the user: the runtime permission on Android 13+, and not blocked in settings. */
fun areNudgeNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun notificationIdFor(nudgeId: String): Int = nudgeId.hashCode()

class AndroidNudgeNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val messages: NudgeMessages,
    ) : NudgeNotifier {
        override fun canNotify(): Boolean = areNudgeNotificationsEnabled(context)

        override fun show(
            nudgeId: String,
            candidate: NudgeCandidate,
            nudgeContext: NudgeContext,
            expression: CoachExpression,
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
                    .setContentText(messages.message(candidate, nudgeContext))
                    .setLargeIcon(renderMascot(expression.mood, largeIconPx()))
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

        private fun largeIconPx(): Int = (LARGE_ICON_DP * context.resources.displayMetrics.density).toInt()

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
