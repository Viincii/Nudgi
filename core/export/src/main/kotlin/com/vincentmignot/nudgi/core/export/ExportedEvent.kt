package com.vincentmignot.nudgi.core.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** One line of `events.jsonl`, written by an export and read back by an import. */
@Serializable
internal data class ExportedEvent(
    val id: Long,
    val timestamp: Long,
    @SerialName("event_type") val eventType: String,
    @SerialName("package_name") val packageName: String?,
    @SerialName("duration_ms") val durationMs: Long,
    val metadata: JsonElement,
)

/**
 * The `metadata` column as it goes into an export: inlined as JSON, so a notebook can flatten it. A
 * row that is not valid JSON is kept verbatim as a string rather than failing the whole export.
 */
internal fun metadataToJson(raw: String): JsonElement =
    try {
        Json.parseToJsonElement(raw)
    } catch (_: SerializationException) {
        JsonPrimitive(raw)
    }

/**
 * The reverse of [metadataToJson]: a string is the verbatim metadata, anything else is re-encoded.
 * Re-encoding is compact, as the metadata written by the app already is, so a row survives an
 * export and an import unchanged.
 */
internal fun metadataFromJson(element: JsonElement): String =
    if (element is JsonPrimitive && element.isString) element.content else element.toString()
