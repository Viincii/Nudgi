package com.vincentmignot.nudgi.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {
    @Upsert
    suspend fun upsert(stats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun observeForDate(date: String): Flow<List<DailyStatsEntity>>

    @Query("SELECT * FROM daily_stats WHERE package_name = :packageName ORDER BY date DESC LIMIT :limit")
    fun observeRecentForPackage(
        packageName: String,
        limit: Int,
    ): Flow<List<DailyStatsEntity>>
}
