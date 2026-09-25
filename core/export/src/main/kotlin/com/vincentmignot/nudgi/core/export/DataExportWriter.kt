package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.OutputStream
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal const val EVENTS_FILE = "events.jsonl"
internal const val DAILY_STATS_FILE = "daily_stats.csv"
internal const val MANIFEST_FILE = "manifest.json"

private const val EVENTS_PAGE_SIZE = 1_000

/** What the caller knows about the app and the moment of the export; the writer fills in the counts. */
data class ExportInfo(
    val databaseSchemaVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val exportedAt: Long,
    val timeZone: String,
)

data class ExportSummary(
    val eventCount: Int,
    val dailyStatsCount: Int,
)

/**
 * Writes a zip with one file per table plus a manifest. Events go to JSON Lines with their metadata
 * inlined as a JSON object rather than an escaped string, so a notebook can flatten it directly.
 * Events are read by id up to the max id seen at the start: rows the pipeline appends meanwhile are
 * left for the next export instead of making the counts drift.
 */
class DataExportWriter(
    private val json: Json = Json,
) {
    suspend fun write(
        output: OutputStream,
        source: ExportSource,
        info: ExportInfo,
    ): ExportSummary {
        val zip = ZipOutputStream(output)
        val writer = zip.bufferedWriter()

        zip.putNextEntry(ZipEntry(EVENTS_FILE))
        val eventCount = writeEvents(writer, source)
        writer.flush()
        zip.closeEntry()

        val dailyStats = source.dailyStats()
        zip.putNextEntry(ZipEntry(DAILY_STATS_FILE))
        writeDailyStats(writer, dailyStats)
        writer.flush()
        zip.closeEntry()

        val summary = ExportSummary(eventCount = eventCount, dailyStatsCount = dailyStats.size)
        zip.putNextEntry(ZipEntry(MANIFEST_FILE))
        writer.write(json.encodeToString(ExportManifest.serializer(), info.toManifest(summary)))
        writer.write("\n")
        writer.flush()
        zip.closeEntry()

        zip.finish()
        return summary
    }

    private suspend fun writeEvents(
        writer: Writer,
        source: ExportSource,
    ): Int {
        val upToId = source.maxEventId() ?: return 0
        var afterId = 0L
        var count = 0
        while (true) {
            val page = source.eventsAfter(afterId, upToId, EVENTS_PAGE_SIZE)
            if (page.isEmpty()) return count
            page.forEach { event ->
                writer.write(json.encodeToString(ExportedEvent.serializer(), event.toExported()))
                writer.write("\n")
            }
            count += page.size
            afterId = page.last().id
        }
    }

    private fun writeDailyStats(
        writer: Writer,
        rows: List<DailyStatsEntity>,
    ) {
        writer.write("date,package_name,usage_ms,nudge_count\n")
        rows.forEach { row ->
            writer.write(
                listOf(row.date, row.packageName, row.usageMs.toString(), row.nudgeCount.toString())
                    .joinToString(",") { csvField(it) },
            )
            writer.write("\n")
        }
    }

    private fun EventEntity.toExported() =
        ExportedEvent(
            id = id,
            timestamp = timestamp,
            eventType = eventType,
            packageName = packageName,
            durationMs = durationMs,
            metadata = parseMetadata(metadata),
        )

    // A row that is not valid JSON is kept verbatim as a string rather than failing the whole export.
    private fun parseMetadata(raw: String): JsonElement =
        try {
            json.parseToJsonElement(raw)
        } catch (_: SerializationException) {
            JsonPrimitive(raw)
        }
}

@Serializable
private data class ExportedEvent(
    val id: Long,
    val timestamp: Long,
    @SerialName("event_type") val eventType: String,
    @SerialName("package_name") val packageName: String?,
    @SerialName("duration_ms") val durationMs: Long,
    val metadata: JsonElement,
)

private fun ExportInfo.toManifest(summary: ExportSummary) =
    ExportManifest(
        formatVersion = EXPORT_FORMAT_VERSION,
        databaseSchemaVersion = databaseSchemaVersion,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        exportedAt = exportedAt,
        timeZone = timeZone,
        eventCount = summary.eventCount,
        dailyStatsCount = summary.dailyStatsCount,
    )

internal fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
