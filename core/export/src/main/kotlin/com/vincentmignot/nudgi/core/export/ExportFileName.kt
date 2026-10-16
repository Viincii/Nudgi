package com.vincentmignot.nudgi.core.export

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/** Suggested name for the file picker; sortable, so successive exports list in order. */
fun exportFileName(now: LocalDateTime): String = "nudgi-export-${now.format(FILE_TIMESTAMP)}.zip"

const val EXPORT_MIME_TYPE = "application/zip"

/** What the import picker offers: some document providers label a zip with an older or a generic type. */
val IMPORT_MIME_TYPES = arrayOf(EXPORT_MIME_TYPE, "application/x-zip-compressed", "application/octet-stream")
