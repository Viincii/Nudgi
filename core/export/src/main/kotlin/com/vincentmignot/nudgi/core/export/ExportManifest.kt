package com.vincentmignot.nudgi.core.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bumped when the layout of an export changes (files, columns, line format), independently of the
 * database schema, so a training script can tell which reader to use.
 */
const val EXPORT_FORMAT_VERSION = 1

/** `manifest.json`: what a script needs to interpret the other files of an export. */
@Serializable
data class ExportManifest(
    // No default value: kotlinx.serialization leaves fields at their default out of the JSON.
    @SerialName("format_version") val formatVersion: Int,
    @SerialName("database_schema_version") val databaseSchemaVersion: Int,
    @SerialName("app_version_name") val appVersionName: String,
    @SerialName("app_version_code") val appVersionCode: Long,
    @SerialName("exported_at") val exportedAt: Long,
    /** Timestamps are epoch milliseconds; this zone turns them back into the local times rules saw. */
    @SerialName("time_zone") val timeZone: String,
    @SerialName("event_count") val eventCount: Int,
    @SerialName("daily_stats_count") val dailyStatsCount: Int,
)
