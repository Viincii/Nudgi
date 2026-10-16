package com.vincentmignot.nudgi.core.export

import android.content.Context
import android.net.Uri
import com.vincentmignot.nudgi.core.database.DatabaseTransactions
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.usagestats.DailyStatsAggregator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** Merges an export the user picked back into the database. */
fun interface DataImporter {
    suspend fun importFrom(source: Uri): ImportSummary
}

/** Reads a document opened through the Storage Access Framework, so no storage permission is needed. */
class DocumentDataImporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val transactions: DatabaseTransactions,
        private val eventDao: EventDao,
        private val aggregator: DailyStatsAggregator,
    ) : DataImporter {
        override suspend fun importFrom(source: Uri): ImportSummary =
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                DataImport().run(
                    open = {
                        resolver.openInputStream(source)?.buffered()
                            ?: throw FileNotFoundException("Cannot open $source")
                    },
                    target = DaoImportTarget(transactions, eventDao, aggregator),
                )
            }
    }
