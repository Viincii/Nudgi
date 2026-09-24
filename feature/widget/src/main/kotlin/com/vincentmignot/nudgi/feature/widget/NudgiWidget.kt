package com.vincentmignot.nudgi.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.mascot.renderMascot
import com.vincentmignot.nudgi.core.today.Today
import com.vincentmignot.nudgi.core.today.TodayRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

private const val MINUTE_MS = 60_000L

/**
 * Widget bitmaps travel to the launcher through a binder transaction capped at about 1 MB, so the
 * mascot is rendered at a fixed size that stays sharp on a 2x2 widget without nearing that cap.
 */
private const val MASCOT_BITMAP_PX = 320

/** Below this height, the widget shows the mascot alone. */
private val MIN_HEIGHT_FOR_USAGE = 100.dp

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NudgiWidgetEntryPoint {
    fun todayRepository(): TodayRepository
}

/**
 * Nudgi on the home screen, in the mood derived from today, with today's watched usage when the
 * widget is tall enough. It shows a snapshot: [NudgiWidgetUpdater] asks for a new one whenever the
 * usage pipeline has written.
 */
class NudgiWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repository =
            EntryPointAccessors.fromApplication(context, NudgiWidgetEntryPoint::class.java).todayRepository()
        val today = repository.observe().first()
        val mascot = renderMascot(today.mood, MASCOT_BITMAP_PX)
        provideContent {
            GlanceTheme {
                NudgiWidgetContent(today = today, mascot = ImageProvider(mascot))
            }
        }
    }
}

@Composable
private fun NudgiWidgetContent(
    today: Today,
    mascot: ImageProvider,
) {
    val context = LocalContext.current
    val launchIntent =
        checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) {
            "Nudgi declares a launcher activity"
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .clickable(actionStartActivity(launchIntent))
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = mascot,
            contentDescription = context.getString(today.mood.descriptionRes),
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
        )
        if (LocalSize.current.height >= MIN_HEIGHT_FOR_USAGE) {
            Text(
                text = usageText(context, today.watchedUsageMs),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

private fun usageText(
    context: Context,
    usageMs: Long,
): String {
    val minutes = (usageMs / MINUTE_MS).toInt()
    return if (minutes < 60) {
        context.getString(R.string.widget_usage_minutes, minutes)
    } else {
        context.getString(R.string.widget_usage_hours, minutes / 60, minutes % 60)
    }
}
