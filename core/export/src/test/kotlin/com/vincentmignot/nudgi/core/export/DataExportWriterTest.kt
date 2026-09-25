package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

class DataExportWriterTest {
    private val info =
        ExportInfo(
            databaseSchemaVersion = 1,
            appVersionName = "0.1.0",
            appVersionCode = 1L,
            exportedAt = 1_758_800_000_000L,
            timeZone = "Europe/Paris",
        )

    private class FakeSource(
        private val events: List<EventEntity>,
        private val stats: List<DailyStatsEntity> = emptyList(),
        /** Simulates rows the pipeline appends after the export read the max id. */
        private val appendedDuringExport: List<EventEntity> = emptyList(),
    ) : ExportSource {
        var pageRequests = 0

        override suspend fun maxEventId(): Long? = events.maxOfOrNull { it.id }

        override suspend fun eventsAfter(
            afterId: Long,
            upToId: Long,
            limit: Int,
        ): List<EventEntity> {
            pageRequests++
            return (events + appendedDuringExport)
                .filter { it.id in (afterId + 1)..upToId }
                .sortedBy { it.id }
                .take(limit)
        }

        override suspend fun dailyStats(): List<DailyStatsEntity> = stats
    }

    private fun event(
        id: Long,
        metadata: String = "{}",
        packageName: String? = "com.example.app",
    ) = EventEntity(
        id = id,
        timestamp = id * 1_000L,
        eventType = "app_foreground",
        packageName = packageName,
        durationMs = 0L,
        metadata = metadata,
    )

    private fun export(source: ExportSource): Pair<ExportSummary, Map<String, String>> {
        val output = ByteArrayOutputStream()
        var summary: ExportSummary? = null
        runTest { summary = DataExportWriter().write(output, source, info) }
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() }
        }
        return summary!! to entries
    }

    @Test
    fun `an empty database exports headers and a manifest with zero counts`() {
        val (summary, files) = export(FakeSource(events = emptyList()))

        assertEquals(ExportSummary(eventCount = 0, dailyStatsCount = 0), summary)
        assertEquals(setOf(EVENTS_FILE, DAILY_STATS_FILE, MANIFEST_FILE), files.keys)
        assertEquals("", files.getValue(EVENTS_FILE))
        assertEquals("date,package_name,usage_ms,nudge_count\n", files.getValue(DAILY_STATS_FILE))
        val manifest = Json.parseToJsonElement(files.getValue(MANIFEST_FILE)).jsonObject
        assertEquals(0, manifest.getValue("event_count").jsonPrimitive.int)
    }

    @Test
    fun `events are written one per line with metadata inlined as an object`() {
        val (_, files) =
            export(FakeSource(events = listOf(event(1, metadata = """{"nudge_id":"n1","level":2}"""))))

        val line = Json.parseToJsonElement(files.getValue(EVENTS_FILE).trim()).jsonObject

        assertEquals(1L, line.getValue("id").jsonPrimitive.long)
        assertEquals("app_foreground", line.getValue("event_type").jsonPrimitive.content)
        val metadata = line.getValue("metadata") as JsonObject
        assertEquals("n1", metadata.getValue("nudge_id").jsonPrimitive.content)
        assertEquals(2, metadata.getValue("level").jsonPrimitive.int)
    }

    @Test
    fun `a null package name is written as JSON null`() {
        val (_, files) = export(FakeSource(events = listOf(event(1, packageName = null))))

        val line = Json.parseToJsonElement(files.getValue(EVENTS_FILE).trim()).jsonObject

        assertEquals(JsonNull, line.getValue("package_name"))
    }

    @Test
    fun `metadata that is not valid JSON is kept as a string`() {
        val (_, files) = export(FakeSource(events = listOf(event(1, metadata = "not json"))))

        val line = Json.parseToJsonElement(files.getValue(EVENTS_FILE).trim()).jsonObject

        assertEquals(JsonPrimitive("not json"), line.getValue("metadata"))
    }

    @Test
    fun `events are read in pages until the source is exhausted`() {
        val source = FakeSource(events = (1L..2_500L).map { event(it) })

        val (summary, files) = export(source)

        assertEquals(2_500, summary.eventCount)
        assertEquals(2_500, files.getValue(EVENTS_FILE).lines().count { it.isNotEmpty() })
        // Three full or partial pages, then an empty one that ends the scan.
        assertEquals(4, source.pageRequests)
    }

    @Test
    fun `events appended during the export are left for the next one`() {
        val source = FakeSource(events = listOf(event(1), event(2)), appendedDuringExport = listOf(event(3)))

        val (summary, _) = export(source)

        assertEquals(2, summary.eventCount)
    }

    @Test
    fun `daily stats are written as CSV and counted in the manifest`() {
        val stats =
            listOf(
                DailyStatsEntity("2026-09-24", "com.example.app", 60_000L, 2),
                DailyStatsEntity("2026-09-25", "com.other.app", 5_000L, 0),
            )

        val (summary, files) = export(FakeSource(events = listOf(event(1)), stats = stats))

        assertEquals(
            "date,package_name,usage_ms,nudge_count\n" +
                "2026-09-24,com.example.app,60000,2\n" +
                "2026-09-25,com.other.app,5000,0\n",
            files.getValue(DAILY_STATS_FILE),
        )
        val manifest = Json.decodeFromString(ExportManifest.serializer(), files.getValue(MANIFEST_FILE))
        assertEquals(
            ExportManifest(
                databaseSchemaVersion = 1,
                appVersionName = "0.1.0",
                appVersionCode = 1L,
                exportedAt = 1_758_800_000_000L,
                timeZone = "Europe/Paris",
                eventCount = 1,
                dailyStatsCount = 2,
            ),
            manifest,
        )
        assertEquals(ExportSummary(eventCount = 1, dailyStatsCount = 2), summary)
    }

    @Test
    fun `csv fields with separators or quotes are quoted`() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
    }

    @Test
    fun `file name sorts by date and time`() {
        assertEquals("nudgi-export-20260925-0907.zip", exportFileName(LocalDateTime.of(2026, 9, 25, 9, 7)))
    }
}
