package com.vincentmignot.nudgi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** Written into exports, so a file can be matched with the schema in `core/database/schemas/`. */
const val NUDGI_DATABASE_VERSION = 1

@Database(
    entities = [EventEntity::class, DailyStatsEntity::class],
    version = NUDGI_DATABASE_VERSION,
    exportSchema = true,
)
abstract class NudgiDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    abstract fun dailyStatsDao(): DailyStatsDao
}
