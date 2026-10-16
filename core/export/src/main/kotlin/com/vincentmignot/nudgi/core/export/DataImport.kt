package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.EventEntity
import java.io.InputStream

private const val IMPORT_PAGE_SIZE = 1_000

data class ImportSummary(
    val addedCount: Int,
    /** Events of the export the database already held, left as they were. */
    val alreadyPresentCount: Int,
)

/**
 * Merges an export into the database. An event is already present when a row has the same content
 * apart from its id: nudge events carry a UUID, and two usage events with the same app, type and
 * millisecond are one system event. Matching counts rows, so an export holding two identical rows
 * restores both, and importing the same file twice adds nothing the second time. Imported rows get
 * new ids, in the export's order.
 *
 * Everything is validated before the first write, and the writes share one transaction: a file that
 * fails halfway, or a cancelled import, leaves the database as it was.
 */
class DataImport(
    private val reader: DataImportReader = DataImportReader(),
) {
    /** [open] is called twice: once to validate the whole file, once to insert its events. */
    suspend fun run(
        open: () -> InputStream,
        target: ImportTarget,
    ): ImportSummary {
        val inspection = open().use { reader.inspect(it) }
        val first = inspection.firstTimestamp ?: return ImportSummary(addedCount = 0, alreadyPresentCount = 0)
        val last = checkNotNull(inspection.lastTimestamp)
        return target.inTransaction {
            val unmatched =
                target
                    .eventsBetween(first, last)
                    .groupingBy { it.identity() }
                    .eachCount()
                    .toMutableMap()
            var added = 0
            var alreadyPresent = 0
            var firstAdded: Long? = null
            open().use { input ->
                reader.readEvents(input, IMPORT_PAGE_SIZE) { page ->
                    val fresh = page.filterNot { unmatched.consume(it.identity()) }
                    alreadyPresent += page.size - fresh.size
                    if (fresh.isNotEmpty()) {
                        target.insertEvents(fresh)
                        added += fresh.size
                        firstAdded = minOf(firstAdded ?: Long.MAX_VALUE, fresh.minOf { it.timestamp })
                    }
                }
            }
            firstAdded?.let { target.refreshDailyStatsSince(it) }
            ImportSummary(addedCount = added, alreadyPresentCount = alreadyPresent)
        }
    }
}

private data class EventIdentity(
    val timestamp: Long,
    val eventType: String,
    val packageName: String?,
    val durationMs: Long,
    val metadata: String,
)

// Metadata goes through the same round trip as an exported row, so a row the database stored with
// other spacing still matches its exported copy.
private fun EventEntity.identity() =
    EventIdentity(timestamp, eventType, packageName, durationMs, metadataFromJson(metadataToJson(metadata)))

/** Takes one occurrence of [key] if any is left: true when the event was already in the database. */
private fun MutableMap<EventIdentity, Int>.consume(key: EventIdentity): Boolean {
    val left = this[key] ?: return false
    if (left == 1) remove(key) else this[key] = left - 1
    return true
}
