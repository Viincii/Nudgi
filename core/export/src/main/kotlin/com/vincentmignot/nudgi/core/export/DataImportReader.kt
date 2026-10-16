package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.database.NUDGI_DATABASE_VERSION
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Export layouts this build can read back. */
internal val READABLE_FORMAT_VERSIONS = setOf(EXPORT_FORMAT_VERSION)

/**
 * Database schemas whose exports this build can read back. Only the current one until the schema
 * changes: an older version joins this set together with the conversion of its rows.
 */
internal val READABLE_SCHEMA_VERSIONS = setOf(NUDGI_DATABASE_VERSION)

/** Why an export cannot be imported. The import writes nothing in any of these cases. */
sealed interface ImportProblem {
    data class MissingFile(
        val name: String,
    ) : ImportProblem

    data object InvalidManifest : ImportProblem

    data class UnsupportedFormat(
        val formatVersion: Int,
    ) : ImportProblem

    data class UnsupportedSchema(
        val databaseSchemaVersion: Int,
    ) : ImportProblem

    /** [line] is 1-based, as an editor shows it. */
    data class InvalidLine(
        val file: String,
        val line: Int,
    ) : ImportProblem

    /** The file holds fewer or more rows than the manifest says: a truncated or edited export. */
    data class CountMismatch(
        val file: String,
        val expected: Int,
        val actual: Int,
    ) : ImportProblem
}

class ImportException(
    val problem: ImportProblem,
) : Exception(problem.toString())

/** What a first read of an export established, before anything is written. */
data class ImportInspection(
    val manifest: ExportManifest,
    val eventCount: Int,
    /** Bounds of the events' timestamps, null when the export has no event. */
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
)

/**
 * Reads an export written by [DataExportWriter]. [inspect] goes through the whole zip and fails on
 * anything an import could trip on halfway; [readEvents] then streams the events in pages. The
 * manifest is the last entry of the zip, so the two need separate reads of the file.
 */
class DataImportReader internal constructor(
    private val readableFormatVersions: Set<Int>,
    private val readableSchemaVersions: Set<Int>,
) {
    constructor() : this(READABLE_FORMAT_VERSIONS, READABLE_SCHEMA_VERSIONS)

    private val manifestJson = Json { ignoreUnknownKeys = true }

    fun inspect(input: InputStream): ImportInspection {
        var manifest: JsonObject? = null
        var events: EventsScan? = null
        var dailyStats: DailyStatsScan? = null
        val zip = ZipInputStream(input)
        generateSequence { zip.nextEntry }.forEach { entry ->
            when (entry.name) {
                MANIFEST_FILE -> manifest = parseManifest(zip.bufferedReader().readText())
                EVENTS_FILE -> events = scanEvents(zip.bufferedReader())
                DAILY_STATS_FILE -> dailyStats = scanDailyStats(zip.bufferedReader())
            }
        }

        // The manifest decides first: a newer export can fail to parse for reasons its version explains.
        val manifestObject = manifest ?: throw ImportException(ImportProblem.MissingFile(MANIFEST_FILE))
        val parsed = checkManifest(manifestObject)
        val eventsScan = events ?: throw ImportException(ImportProblem.MissingFile(EVENTS_FILE))
        val statsScan = dailyStats ?: throw ImportException(ImportProblem.MissingFile(DAILY_STATS_FILE))
        eventsScan.invalidLine?.let { throw ImportException(ImportProblem.InvalidLine(EVENTS_FILE, it)) }
        statsScan.invalidLine?.let { throw ImportException(ImportProblem.InvalidLine(DAILY_STATS_FILE, it)) }
        checkCount(EVENTS_FILE, parsed.eventCount, eventsScan.count)
        checkCount(DAILY_STATS_FILE, parsed.dailyStatsCount, statsScan.count)
        return ImportInspection(
            manifest = parsed,
            eventCount = eventsScan.count,
            firstTimestamp = eventsScan.firstTimestamp,
            lastTimestamp = eventsScan.lastTimestamp,
        )
    }

    /** Streams the events in the export's order, as rows without an id so the database assigns one. */
    suspend fun readEvents(
        input: InputStream,
        pageSize: Int,
        onPage: suspend (List<EventEntity>) -> Unit,
    ) {
        val zip = ZipInputStream(input)
        generateSequence { zip.nextEntry }.firstOrNull { it.name == EVENTS_FILE }
            ?: throw ImportException(ImportProblem.MissingFile(EVENTS_FILE))
        val page = ArrayList<EventEntity>(pageSize)
        zip.bufferedReader().lineSequence().forEachIndexed { index, line ->
            val event =
                parseEvent(line) ?: throw ImportException(ImportProblem.InvalidLine(EVENTS_FILE, index + 1))
            page += event.toEntity()
            if (page.size == pageSize) {
                onPage(page.toList())
                page.clear()
            }
        }
        if (page.isNotEmpty()) onPage(page.toList())
    }

    private fun parseManifest(text: String): JsonObject =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: throw ImportException(ImportProblem.InvalidManifest)

    private fun checkManifest(manifest: JsonObject): ExportManifest {
        val formatVersion =
            manifest["format_version"]?.jsonPrimitive?.intOrNull
                ?: throw ImportException(ImportProblem.InvalidManifest)
        if (formatVersion !in readableFormatVersions) {
            throw ImportException(ImportProblem.UnsupportedFormat(formatVersion))
        }
        val parsed =
            try {
                manifestJson.decodeFromJsonElement(ExportManifest.serializer(), manifest)
            } catch (_: SerializationException) {
                throw ImportException(ImportProblem.InvalidManifest)
            } catch (_: IllegalArgumentException) {
                throw ImportException(ImportProblem.InvalidManifest)
            }
        if (parsed.databaseSchemaVersion !in readableSchemaVersions) {
            throw ImportException(ImportProblem.UnsupportedSchema(parsed.databaseSchemaVersion))
        }
        return parsed
    }

    private fun checkCount(
        file: String,
        expected: Int,
        actual: Int,
    ) {
        if (expected != actual) throw ImportException(ImportProblem.CountMismatch(file, expected, actual))
    }

    private fun scanEvents(reader: BufferedReader): EventsScan {
        var count = 0
        var first: Long? = null
        var last: Long? = null
        var invalidLine: Int? = null
        reader.lineSequence().forEachIndexed { index, line ->
            val event = parseEvent(line)
            if (event == null) {
                if (invalidLine == null) invalidLine = index + 1
            } else {
                first = minOf(first ?: event.timestamp, event.timestamp)
                last = maxOf(last ?: event.timestamp, event.timestamp)
            }
            count++
        }
        return EventsScan(count, first, last, invalidLine)
    }

    // Only the shape is checked: the rows are recomputed from the events, so their values do not matter.
    private fun scanDailyStats(reader: BufferedReader): DailyStatsScan {
        val records = csvRecords(reader).iterator()
        if (!records.hasNext() || records.next() != DAILY_STATS_HEADER.split(",")) {
            return DailyStatsScan(count = 0, invalidLine = 1)
        }
        var count = 0
        var invalidLine: Int? = null
        records.forEach { record ->
            count++
            if (invalidLine == null && record.size != DAILY_STATS_COLUMNS) invalidLine = count + 1
        }
        return DailyStatsScan(count, invalidLine)
    }

    private fun parseEvent(line: String): ExportedEvent? =
        try {
            Json.decodeFromString(ExportedEvent.serializer(), line)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private class EventsScan(
        val count: Int,
        val firstTimestamp: Long?,
        val lastTimestamp: Long?,
        val invalidLine: Int?,
    )

    private class DailyStatsScan(
        val count: Int,
        val invalidLine: Int?,
    )
}

private const val DAILY_STATS_COLUMNS = 4

internal fun ExportedEvent.toEntity() =
    EventEntity(
        timestamp = timestamp,
        eventType = eventType,
        packageName = packageName,
        durationMs = durationMs,
        metadata = metadataFromJson(metadata),
    )

/**
 * Splits CSV as [csvField] writes it: fields quoted when they hold a comma, a quote or a line break,
 * quotes doubled inside. A line break inside quotes stays in the field instead of ending the record.
 */
internal fun csvRecords(reader: BufferedReader): Sequence<List<String>> =
    sequence {
        val field = StringBuilder()
        val record = mutableListOf<String>()
        var inQuotes = false
        var pendingRecord = false
        while (true) {
            val c = reader.read()
            if (c == -1) break
            val char = c.toChar()
            pendingRecord = true
            when {
                inQuotes && char == '"' -> {
                    reader.mark(1)
                    if (reader.read() == '"'.code) {
                        field.append('"')
                    } else {
                        reader.reset()
                        inQuotes = false
                    }
                }

                inQuotes -> {
                    field.append(char)
                }

                char == '"' -> {
                    inQuotes = true
                }

                char == ',' -> {
                    record += field.toString()
                    field.clear()
                }

                char == '\n' -> {
                    record += field.toString()
                    field.clear()
                    yield(record.toList())
                    record.clear()
                    pendingRecord = false
                }

                char != '\r' -> {
                    field.append(char)
                }
            }
        }
        if (pendingRecord) {
            record += field.toString()
            yield(record.toList())
        }
    }
