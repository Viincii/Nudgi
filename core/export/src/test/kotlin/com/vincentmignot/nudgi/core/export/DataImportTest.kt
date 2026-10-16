package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DataImportTest {
    private val info =
        ExportInfo(
            databaseSchemaVersion = 1,
            appVersionName = "0.1.0",
            appVersionCode = 1L,
            exportedAt = 1_758_800_000_000L,
            timeZone = "Europe/Paris",
        )

    /** An in-memory table with Room's autoincrement and a transaction that rolls back on failure. */
    private class FakeTarget(
        initial: List<EventEntity> = emptyList(),
    ) : ImportTarget {
        val rows = initial.toMutableList()
        var refreshedSince: Long? = null
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

        override suspend fun <R> inTransaction(block: suspend () -> R): R {
            val snapshot = rows.toList()
            val idSnapshot = nextId
            return try {
                block()
            } catch (e: Throwable) {
                rows.clear()
                rows += snapshot
                nextId = idSnapshot
                throw e
            }
        }

        override suspend fun eventsBetween(
            startInclusive: Long,
            endInclusive: Long,
        ) = rows.filter { it.timestamp in startInclusive..endInclusive }

        override suspend fun insertEvents(events: List<EventEntity>) {
            events.forEach { rows += it.copy(id = nextId++) }
        }

        override suspend fun refreshDailyStatsSince(since: Long) {
            refreshedSince = since
        }
    }

    private class ListSource(
        private val events: List<EventEntity>,
        private val stats: List<DailyStatsEntity> = emptyList(),
    ) : ExportSource {
        override suspend fun maxEventId(): Long? = events.maxOfOrNull { it.id }

        override suspend fun eventsAfter(
            afterId: Long,
            upToId: Long,
            limit: Int,
        ) = events.filter { it.id in (afterId + 1)..upToId }.sortedBy { it.id }.take(limit)

        override suspend fun dailyStats() = stats
    }

    private fun event(
        id: Long,
        timestamp: Long = id * 1_000L,
        eventType: String = "app_foreground",
        metadata: String = "{}",
        packageName: String? = "com.example.app",
    ) = EventEntity(
        id = id,
        timestamp = timestamp,
        eventType = eventType,
        packageName = packageName,
        durationMs = id * 10L,
        metadata = metadata,
    )

    private fun exportOf(
        events: List<EventEntity>,
        stats: List<DailyStatsEntity> = emptyList(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        runTest { DataExportWriter().write(output, ListSource(events, stats), info) }
        return output.toByteArray()
    }

    private fun entriesOf(zip: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.forEach { entries[it.name] = input.readBytes().decodeToString() }
        }
        return entries
    }

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun opener(bytes: ByteArray): () -> InputStream = { ByteArrayInputStream(bytes) }

    private fun import(
        bytes: ByteArray,
        target: FakeTarget,
    ): ImportSummary {
        var summary: ImportSummary? = null
        runTest { summary = DataImport().run(opener(bytes), target) }
        return summary!!
    }

    private fun problemOf(
        bytes: ByteArray,
        target: FakeTarget = FakeTarget(),
    ): ImportProblem {
        try {
            import(bytes, target)
        } catch (e: ImportException) {
            return e.problem
        }
        fail("The import should have been refused")
        error("unreachable")
    }

    private fun List<EventEntity>.withoutIds() = map { it.copy(id = 0) }

    private val sample =
        listOf(
            event(1),
            event(
                2,
                eventType = "nudge_shown",
                metadata = """{"nudge_id":"a-1","level":2,"probabilities":[0.8,0.2]}""",
            ),
            event(3, metadata = "not json, kept verbatim"),
            event(4, packageName = null, metadata = """{"text":"comma, \"quote\"\nline"}"""),
        )

    @Test
    fun `an export imported into an empty database gives back the same events`() {
        val target = FakeTarget()

        val summary = import(exportOf(sample), target)

        assertEquals(ImportSummary(addedCount = 4, alreadyPresentCount = 0), summary)
        assertEquals(sample, target.rows)
        assertEquals(1_000L, target.refreshedSince)
    }

    @Test
    fun `importing the same export twice adds nothing the second time`() {
        val target = FakeTarget()
        val export = exportOf(sample)
        import(export, target)
        target.refreshedSince = null

        val summary = import(export, target)

        assertEquals(ImportSummary(addedCount = 0, alreadyPresentCount = 4), summary)
        assertEquals(sample, target.rows)
        assertNull(target.refreshedSince)
    }

    @Test
    fun `rows recorded since install stay and the export comes after them`() {
        val recordedSinceInstall = event(1, timestamp = 99_000L, eventType = "app_background")
        val target = FakeTarget(listOf(recordedSinceInstall))

        val summary = import(exportOf(sample), target)

        assertEquals(4, summary.addedCount)
        assertEquals(recordedSinceInstall, target.rows.first())
        assertEquals(sample.withoutIds(), target.rows.drop(1).withoutIds())
        assertEquals(listOf(2L, 3L, 4L, 5L), target.rows.drop(1).map { it.id })
    }

    @Test
    fun `only the rows the database lacks are added`() {
        val target = FakeTarget(listOf(sample[1].copy(id = 7), sample[3].copy(id = 8)))

        val summary = import(exportOf(sample), target)

        assertEquals(ImportSummary(addedCount = 2, alreadyPresentCount = 2), summary)
        assertEquals(sample.withoutIds().toSet(), target.rows.withoutIds().toSet())
        assertEquals(sample[0].timestamp, target.refreshedSince)
    }

    @Test
    fun `identical rows count one by one`() {
        val twins = listOf(event(1), event(1).copy(id = 2))
        val target = FakeTarget(listOf(event(1).copy(id = 5)))

        val summary = import(exportOf(twins), target)

        assertEquals(ImportSummary(addedCount = 1, alreadyPresentCount = 1), summary)
        assertEquals(2, target.rows.size)
    }

    @Test
    fun `metadata stored with other spacing still matches its exported copy`() {
        val spaced = event(2, metadata = """{ "nudge_id": "a-1" }""")
        val target = FakeTarget(listOf(spaced))

        val summary = import(exportOf(listOf(spaced)), target)

        assertEquals(ImportSummary(addedCount = 0, alreadyPresentCount = 1), summary)
    }

    @Test
    fun `an export without events imports nothing`() {
        val target = FakeTarget()

        assertEquals(ImportSummary(addedCount = 0, alreadyPresentCount = 0), import(exportOf(emptyList()), target))
        assertTrue(target.rows.isEmpty())
    }

    @Test
    fun `daily stats are checked but not imported`() {
        val stats = listOf(DailyStatsEntity("2026-10-01", "com.example, \"quoted\"", 60_000L, 2))
        val target = FakeTarget()

        import(exportOf(sample, stats), target)

        assertEquals(4, target.rows.size)
    }

    @Test
    fun `a newer export format is refused`() {
        val entries = entriesOf(exportOf(sample)).toMutableMap()
        entries[MANIFEST_FILE] = entries.getValue(MANIFEST_FILE).replace("\"format_version\":1", "\"format_version\":2")

        assertEquals(ImportProblem.UnsupportedFormat(2), problemOf(zipOf(entries)))
    }

    @Test
    fun `an export of another database schema is refused`() {
        val entries = entriesOf(exportOf(sample)).toMutableMap()
        entries[MANIFEST_FILE] =
            entries.getValue(MANIFEST_FILE).replace("\"database_schema_version\":1", "\"database_schema_version\":2")

        assertEquals(ImportProblem.UnsupportedSchema(2), problemOf(zipOf(entries)))
    }

    @Test
    fun `the format version is checked before the lines a newer format could change`() {
        val entries = entriesOf(exportOf(sample)).toMutableMap()
        entries[MANIFEST_FILE] = entries.getValue(MANIFEST_FILE).replace("\"format_version\":1", "\"format_version\":2")
        entries[EVENTS_FILE] = "{\"a_new_layout\":true}\n"

        assertEquals(ImportProblem.UnsupportedFormat(2), problemOf(zipOf(entries)))
    }

    @Test
    fun `a truncated events file is refused`() {
        val entries = entriesOf(exportOf(sample)).toMutableMap()
        entries[EVENTS_FILE] =
            entries
                .getValue(EVENTS_FILE)
                .lines()
                .take(2)
                .joinToString("\n", postfix = "\n")

        assertEquals(ImportProblem.CountMismatch(EVENTS_FILE, expected = 4, actual = 2), problemOf(zipOf(entries)))
    }

    @Test
    fun `a malformed event line is refused with its line number`() {
        val entries = entriesOf(exportOf(sample)).toMutableMap()
        val lines = entries.getValue(EVENTS_FILE).lines().toMutableList()
        lines[2] = lines[2].replace("\"timestamp\":3000", "\"timestamp\":\"soon\"")
        entries[EVENTS_FILE] = lines.joinToString("\n")

        assertEquals(ImportProblem.InvalidLine(EVENTS_FILE, 3), problemOf(zipOf(entries)))
    }

    @Test
    fun `a daily stats file with another header is refused`() {
        val entries = entriesOf(exportOf(sample)).toMutableMap()
        entries[DAILY_STATS_FILE] = "date,usage_ms\n"

        assertEquals(ImportProblem.InvalidLine(DAILY_STATS_FILE, 1), problemOf(zipOf(entries)))
    }

    @Test
    fun `a zip without a manifest is refused`() {
        val entries = entriesOf(exportOf(sample)) - MANIFEST_FILE

        assertEquals(ImportProblem.MissingFile(MANIFEST_FILE), problemOf(zipOf(entries)))
    }

    @Test
    fun `a file that is not a zip is refused`() {
        assertEquals(ImportProblem.MissingFile(MANIFEST_FILE), problemOf("hello".toByteArray()))
    }

    @Test
    fun `an unreadable manifest is refused`() {
        val entries = entriesOf(exportOf(sample)) + (MANIFEST_FILE to "[1, 2]")

        assertEquals(ImportProblem.InvalidManifest, problemOf(zipOf(entries)))
    }

    @Test
    fun `a file that changes between the two reads leaves the database as it was`() {
        val existing = event(1, timestamp = 99_000L)
        val target = FakeTarget(listOf(existing))
        val valid = exportOf(List(1_500) { event(it + 2L) })
        val entries = entriesOf(valid).toMutableMap()
        entries[EVENTS_FILE] = entries
            .getValue(EVENTS_FILE)
            .lines()
            .take(1_200)
            .joinToString("\n") + "\n{oops}\n"
        val broken = zipOf(entries)
        var reads = 0

        try {
            runTest { DataImport().run({ ByteArrayInputStream(if (reads++ == 0) valid else broken) }, target) }
            fail("The import should have failed")
        } catch (e: ImportException) {
            assertEquals(ImportProblem.InvalidLine(EVENTS_FILE, 1_201), e.problem)
        }
        assertEquals(listOf(existing), target.rows)
    }

    @Test
    fun `csv records keep quoted commas, quotes and line breaks`() {
        val text = "a,b\n\"x, y\",\"say \"\"hi\"\"\"\n\"two\nlines\",z"

        val records = csvRecords(text.reader().buffered()).toList()

        assertEquals(listOf(listOf("a", "b"), listOf("x, y", "say \"hi\""), listOf("two\nlines", "z")), records)
    }
}
