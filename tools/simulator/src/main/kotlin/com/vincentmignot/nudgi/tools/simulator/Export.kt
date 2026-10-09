package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.BanditContext
import com.vincentmignot.nudgi.core.bandit.RewardV1
import com.vincentmignot.nudgi.core.bandit.UsageInterval
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.zip.ZipFile

private const val MERGE_GAP_MS = 60_000L

// Mirrors tools/bandit_report.py: the export does not say which apps were watched, so the apps
// the rules nudged on are, plus the known feed apps.
private val KNOWN_FEED_APPS =
    setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android",
    )
private val NEVER_WATCHED = setOf("com.google.android.apps.messaging")

/** A real decision of an export, with what the rules did and its benefit once its window has closed. */
data class ExportedDecision(
    val decision: SimDecision,
    val action: BanditAction,
    val benefit: Double?,
)

/**
 * The decisions of an export as the bandit learns from them (`banditObservations` in `core:nudge`):
 * shown nudges, held-out ones, and the first cooldown suppression of a rule, level and session.
 * Decisions listed in `tools/excluded_decisions.json` keep their context but lose their benefit:
 * their outcome was caused by testing the app.
 */
fun readExport(
    path: File,
    excludedIds: Set<String>,
): List<ExportedDecision> {
    val (manifest, events) =
        ZipFile(path).use { zip ->
            fun text(name: String) = zip.getInputStream(zip.getEntry(name)).bufferedReader().readText()
            Json.parseToJsonElement(text("manifest.json")).jsonObject to
                text("events.jsonl")
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .map {
                        Json.parseToJsonElement(it).jsonObject
                    }.toList()
        }
    val exportedAt = manifest.long("exported_at")!!
    val sorted = events.sortedWith(compareBy({ it.long("timestamp") }, { it.long("id") }))
    val nudged = sorted.filter { it.isDecision() }.mapNotNull { it.string("package_name") }.toSet()
    val watched = (nudged + KNOWN_FEED_APPS) - NEVER_WATCHED
    val intervals = watchedIntervals(sorted, watched, exportedAt)
    val seenCooldowns = mutableSetOf<List<Any?>>()

    return sorted.filter { it.isDecision() }.mapNotNull { event ->
        val metadata = event["metadata"]?.jsonObject ?: return@mapNotNull null
        val snapshot = metadata["context"]?.jsonObject ?: return@mapNotNull null
        val timestamp = event.long("timestamp")!!
        val ruleId = metadata.string("rule_id") ?: return@mapNotNull null
        val action =
            if (event.string("event_type") == "nudge_shown") {
                frictionAction(metadata.int("friction_level") ?: 0)
            } else {
                when (metadata.string("reason")) {
                    "holdout" -> {
                        BanditAction.Nothing
                    }

                    "cooldown" -> {
                        val sessionStart = timestamp - snapshot.long("session_ms")!!
                        val key =
                            listOf(
                                ruleId,
                                metadata.int("level"),
                                event.string("package_name"),
                                sessionStart / MERGE_GAP_MS,
                            )
                        if (!seenCooldowns.add(key)) return@mapNotNull null
                        BanditAction.Nothing
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }
            }
        val hour = snapshot.int("local_hour")!!
        val reached = snapshot.int("friction_level_reached") ?: 0
        val context =
            BanditContext(
                localHour = hour,
                weekday = snapshot.int("weekday")!!,
                sessionMs = snapshot.long("session_ms")!!,
                dailyMs = snapshot.long("daily_ms")!!,
                lateNightMs = snapshot.long("late_night_ms")!!,
                nudgesToday = snapshot.int("nudges_today")!!,
                msSinceLastNudge = snapshot.long("ms_since_last_nudge"),
                // Older rows lack it; the app derives it from the responses, a resampled context can do without.
                snoozesToday = snapshot.int("snoozes_today") ?: 0,
                frictionLevelReached = reached,
                ruleId = ruleId,
            )
        val closed = exportedAt >= timestamp + RewardV1.windowMs(hour) + MERGE_GAP_MS
        val excluded = metadata.string("nudge_id") in excludedIds
        ExportedDecision(
            decision =
                SimDecision(
                    context,
                    requestedLevel =
                        maxOf(
                            reached,
                            metadata.int("requested_friction_level") ?: reached,
                        ),
                ),
            action = action,
            benefit = if (closed && !excluded) RewardV1.benefit(timestamp, hour, intervals) else null,
        )
    }
}

/** The ids of `tools/excluded_decisions.json`, an object keyed by nudge id; none if it is missing. */
fun readExcludedIds(file: File): Set<String> =
    if (file.exists()) {
        when (val json = Json.parseToJsonElement(file.readText())) {
            is JsonObject -> json.keys
            is JsonArray -> json.map { it.jsonPrimitive.content }.toSet()
            else -> emptySet()
        }
    } else {
        emptySet()
    }

private fun watchedIntervals(
    events: List<JsonObject>,
    watched: Set<String>,
    exportedAt: Long,
): List<UsageInterval> {
    val closed =
        events
            .filter { it.string("event_type") == "app_background" && it.string("package_name") in watched }
            .mapNotNull { event ->
                val end = event.long("timestamp")!!
                val duration = event.long("duration_ms") ?: 0
                if (duration > 0) UsageInterval(end - duration, end) else null
            }
    // A watched session still open at export time runs until the export.
    val last = events.lastOrNull { it.string("event_type") in setOf("app_foreground", "app_background") }
    val open =
        last
            ?.takeIf { it.string("event_type") == "app_foreground" && it.string("package_name") in watched }
            ?.let { UsageInterval(it.long("timestamp")!!, exportedAt) }
    return closed + listOfNotNull(open)
}

private fun JsonObject.isDecision() = string("event_type") in setOf("nudge_shown", "nudge_suppressed")

private fun JsonObject.string(key: String): String? =
    this[key]
        ?.takeUnless {
            it is JsonObject
        }?.jsonPrimitive
        ?.contentOrNull

private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
