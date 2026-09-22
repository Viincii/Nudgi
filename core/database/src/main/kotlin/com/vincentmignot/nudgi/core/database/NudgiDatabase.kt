package com.vincentmignot.nudgi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class, DailyStatsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NudgiDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    abstract fun dailyStatsDao(): DailyStatsDao
}
