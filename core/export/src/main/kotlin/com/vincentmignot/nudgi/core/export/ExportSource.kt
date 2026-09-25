package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.DailyStatsEntity
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity

/** The rows an export reads, kept narrow so [DataExportWriter] can be tested without a database. */
interface ExportSource {
    suspend fun maxEventId(): Long?

    suspend fun eventsAfter(
        afterId: Long,
        upToId: Long,
        limit: Int,
    ): List<EventEntity>

    suspend fun dailyStats(): List<DailyStatsEntity>
}

internal class DaoExportSource(
    private val eventDao: EventDao,
    private val dailyStatsDao: DailyStatsDao,
) : ExportSource {
    override suspend fun maxEventId(): Long? = eventDao.maxId()

    override suspend fun eventsAfter(
        afterId: Long,
        upToId: Long,
        limit: Int,
    ): List<EventEntity> = eventDao.pageByIdAfter(afterId, upToId, limit)

    override suspend fun dailyStats(): List<DailyStatsEntity> = dailyStatsDao.all()
}
