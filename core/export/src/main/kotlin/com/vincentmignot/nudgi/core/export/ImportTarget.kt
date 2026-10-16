package com.vincentmignot.nudgi.core.export

import com.vincentmignot.nudgi.core.database.DatabaseTransactions
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.EventEntity
import com.vincentmignot.nudgi.core.usagestats.DailyStatsAggregator

/** What an import writes to, kept narrow so [DataImport] can be tested without a database. */
interface ImportTarget {
    suspend fun <R> inTransaction(block: suspend () -> R): R

    suspend fun eventsBetween(
        startInclusive: Long,
        endInclusive: Long,
    ): List<EventEntity>

    suspend fun insertEvents(events: List<EventEntity>)

    /** Recomputes `daily_stats` for every day from the one holding [since]. */
    suspend fun refreshDailyStatsSince(since: Long)
}

internal class DaoImportTarget(
    private val transactions: DatabaseTransactions,
    private val eventDao: EventDao,
    private val aggregator: DailyStatsAggregator,
) : ImportTarget {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = transactions.run(block)

    override suspend fun eventsBetween(
        startInclusive: Long,
        endInclusive: Long,
    ): List<EventEntity> = eventDao.betweenInclusive(startInclusive, endInclusive)

    override suspend fun insertEvents(events: List<EventEntity>) = eventDao.insertAll(events)

    override suspend fun refreshDailyStatsSince(since: Long) = aggregator.aggregateSince(since)
}
