package com.vincentmignot.nudgi.core.usagestats

import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeEventDao : EventDao {
    val inserted = mutableListOf<EventEntity>()

    override suspend fun insert(event: EventEntity): Long {
        inserted += event
        return inserted.size.toLong()
    }

    override suspend fun insertAll(events: List<EventEntity>) {
        inserted += events
    }

    override fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<EventEntity>> = flowOf(inserted.filter { it.timestamp in startInclusive until endExclusive })

    override suspend fun since(sinceInclusive: Long): List<EventEntity> =
        inserted.filter { it.timestamp >= sinceInclusive }.sortedBy { it.timestamp }

    override suspend fun ofTypeSince(
        eventType: String,
        sinceInclusive: Long,
    ): List<EventEntity> =
        inserted
            .filter { it.eventType == eventType && it.timestamp >= sinceInclusive }
            .sortedBy { it.timestamp }

    override suspend fun latest(limit: Int): List<EventEntity> = inserted.takeLast(limit)
}
