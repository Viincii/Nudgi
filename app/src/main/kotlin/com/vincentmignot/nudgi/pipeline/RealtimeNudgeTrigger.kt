package com.vincentmignot.nudgi.pipeline

import com.vincentmignot.nudgi.core.accessibility.ForegroundAppListener
import com.vincentmignot.nudgi.core.nudge.NudgeResponse
import com.vincentmignot.nudgi.core.nudge.NudgeResponseListener
import com.vincentmignot.nudgi.core.nudge.WatchedApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Usage events reach `UsageStatsManager` slightly after the window change the accessibility
 * service sees. Polling right away could close the poll window before the foreground event is
 * recorded, and that event would then never be read.
 */
private const val SETTLE_MS = 2_000L

/**
 * Runs the pipeline as soon as a watched app comes to the foreground, then again whenever a rule
 * could fire, for as long as the app stays there. The periodic worker keeps running on its own and
 * catches anything this misses, e.g. while the accessibility service is off.
 *
 * A snooze tapped in the notification shade reschedules too: the shade is not a foreground
 * change, and without it the follow-up would wait for whatever wake-up was computed before.
 *
 * [onForegroundApp] is called on the main thread by the accessibility service; the state below is
 * only touched there.
 */
@Singleton
class RealtimeNudgeTrigger internal constructor(
    private val pipeline: PipelineRunner,
    private val watchedApps: WatchedApps,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) : ForegroundAppListener,
    NudgeResponseListener {
    @Inject
    constructor(
        pipeline: UsagePipeline,
        watchedApps: WatchedApps,
    ) : this(
        pipeline,
        watchedApps,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        System::currentTimeMillis,
    )

    private var currentPackage: String? = null
    private var job: Job? = null

    override fun onForegroundApp(packageName: String) {
        // Window changes within the same app, e.g. a dialog, keep the schedule running.
        if (packageName == currentPackage && job?.isActive == true) return
        currentPackage = packageName
        job?.cancel()
        job = if (watchedApps.isWatched(packageName)) scope.launch { runWhileInForeground(SETTLE_MS) } else null
    }

    override fun onNudgeResponse(
        packageName: String,
        response: NudgeResponse,
    ) {
        // Only a snooze moves the next evaluation earlier. The response is already in `events`,
        // so there is nothing to settle before running.
        if (response != NudgeResponse.Snooze) return
        scope.launch {
            if (packageName != currentPackage || !watchedApps.isWatched(packageName)) return@launch
            job?.cancel()
            job = scope.launch { runWhileInForeground(settleMs = 0L) }
        }
    }

    private suspend fun runWhileInForeground(settleMs: Long) {
        delay(settleMs)
        var next = pipeline.run()
        while (next != null) {
            delay((next - clock()).coerceAtLeast(0L))
            next = pipeline.run()
        }
    }
}
