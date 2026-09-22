package com.vincentmignot.nudgi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single raw, append-only occurrence: an app coming to the foreground, a nudge shown, a nudge
 * dismissed, and so on. [metadata] carries whatever event-specific context does not warrant its own
 * column, as a JSON object, so that context, the action taken and its outcome can be reconstructed
 * later for the on-device bandit.
 */
@Entity(
    tableName = "events",
    indices = [Index("timestamp"), Index("package_name")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "metadata")
    val metadata: String,
)
