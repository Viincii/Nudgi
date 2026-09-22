package com.vincentmignot.nudgi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Per-app usage totals for one device-local calendar [date] (ISO-8601, `yyyy-MM-dd`), refreshed by
 * a WorkManager job that aggregates [EventEntity] rows. Read by the UI instead of scanning raw
 * events every time.
 */
@Entity(
    tableName = "daily_stats",
    primaryKeys = ["date", "package_name"],
)
data class DailyStatsEntity(
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "usage_ms")
    val usageMs: Long,
    @ColumnInfo(name = "nudge_count")
    val nudgeCount: Int,
)
