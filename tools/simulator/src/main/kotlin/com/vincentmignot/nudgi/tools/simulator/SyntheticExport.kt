package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val MINUTE_MS = 60_000L
private const val PACKAGE = "com.instagram.android"
private val ZONE: ZoneId = ZoneId.of("Europe/Paris")
private val FIRST_DAY: LocalDate = LocalDate.of(2026, 1, 1)

/**
 * Writes [logged] as an export in the real format, which `tools/bandit_report.py` reads unchanged:
 * every decision with its context, the rules' action and the shadow's choice, and watched-app use
 * around it that gives exactly the simulated benefit. Decisions are two days apart at their local
 * hour: a day apart, one at 23:30 and the next at 00:30 would share their windows.
 */
fun writeSyntheticExport(
    file: File,
    logged: List<LoggedDecision>,
    holdoutProbability: Double,
    escalationProbability: Double,
) {
    val events = mutableListOf<Pair<Long, JsonObject>>()

    fun event(
        timestamp: Long,
        type: String,
        durationMs: Long = 0,
        metadata: JsonObject = JsonObject(emptyMap()),
    ) {
        events +=
            timestamp to
            buildJsonObject {
                put("timestamp", timestamp)
                put("event_type", type)
                put("package_name", PACKAGE)
                put("duration_ms", durationMs)
                put("metadata", metadata)
            }
    }

    logged.forEachIndexed { index, entry ->
        val context = entry.decision.context
        val t =
            FIRST_DAY
                .plusDays(2L * index)
                .atTime(context.localHour, 30)
                .atZone(ZONE)
                .toInstant()
                .toEpochMilli()
        val sessionStart = t - context.sessionMs
        // The session that led to the decision, carried on or stopped at it, then any return.
        val (carriedOn, returns) = entry.outcome.watchedAfter.partition { it.start == 0L }
        val sessionEnd = t + (carriedOn.firstOrNull()?.end ?: 0)
        event(sessionStart, "app_foreground")
        event(sessionEnd, "app_background", durationMs = sessionEnd - sessionStart)
        returns.forEach {
            event(t + it.start, "app_foreground")
            event(t + it.end, "app_background", durationMs = it.end - it.start)
        }
        event(
            t,
            if (entry.rulesAction == BanditAction.Nothing) "nudge_suppressed" else "nudge_shown",
            metadata = decisionMetadata(index, entry, holdoutProbability, escalationProbability),
        )
    }

    val sorted = events.sortedBy { it.first }
    val exportedAt = sorted.last().first + 3 * 60 * MINUTE_MS
    file.parentFile?.mkdirs()
    ZipOutputStream(file.outputStream()).use { zip ->
        val writer = zip.bufferedWriter()
        zip.putNextEntry(ZipEntry("events.jsonl"))
        sorted.forEachIndexed { index, (_, event) ->
            writer.write(JsonObject(mapOf("id" to JsonPrimitive(index + 1L)) + event).toString())
            writer.write("\n")
        }
        writer.flush()
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("daily_stats.csv"))
        writer.write("date,package_name,usage_ms,nudge_count\n")
        writer.flush()
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("manifest.json"))
        writer.write(
            buildJsonObject {
                put("format_version", 1)
                put("database_schema_version", 0)
                put("app_version_name", "simulator")
                put("app_version_code", 0)
                put("exported_at", exportedAt)
                put("time_zone", ZONE.id)
                put("event_count", sorted.size)
                put("daily_stats_count", 0)
            }.toString(),
        )
        writer.write("\n")
        writer.flush()
        zip.closeEntry()
    }
}

private fun decisionMetadata(
    index: Int,
    entry: LoggedDecision,
    holdoutProbability: Double,
    escalationProbability: Double,
): JsonObject {
    val decision = entry.decision
    val context = decision.context
    return buildJsonObject {
        put("nudge_id", "sim-${index + 1}")
        put("policy_id", "rules_v4")
        put("rule_id", context.ruleId)
        put("level", 1)
        put("threshold_ms", 0)
        if (entry.rulesAction == BanditAction.Nothing) put("reason", "holdout")
        put("holdout_probability", holdoutProbability)
        putJsonObject("context") {
            put("session_ms", context.sessionMs)
            put("daily_ms", context.dailyMs)
            put("late_night_ms", context.lateNightMs)
            put("local_hour", context.localHour)
            put("weekday", context.weekday)
            put("nudges_today", context.nudgesToday)
            context.msSinceLastNudge?.let { put("ms_since_last_nudge", it) }
            put("snoozes_today", context.snoozesToday)
            put("friction_level_reached", context.frictionLevelReached)
        }
        putJsonObject("shadow") {
            put("policy_id", "lin_ts_v1")
            put("action", entry.shadow.action.id)
            put("propensity", entry.shadow.propensity)
            put("trained_on", entry.shadow.trainedOn)
        }
        frictionLevel(entry.rulesAction)?.let { put("friction_level", it) }
        put("requested_friction_level", decision.requestedLevel)
        put("escalation_probability", escalationProbability)
    }
}
