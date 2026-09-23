package com.vincentmignot.nudgi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE timestamp BETWEEN :startInclusive AND :endExclusive ORDER BY timestamp")
    fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE timestamp >= :sinceInclusive ORDER BY timestamp, id")
    suspend fun since(sinceInclusive: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE event_type = :eventType AND timestamp >= :sinceInclusive ORDER BY timestamp")
    suspend fun ofTypeSince(
        eventType: String,
        sinceInclusive: Long,
    ): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<EventEntity>
}
