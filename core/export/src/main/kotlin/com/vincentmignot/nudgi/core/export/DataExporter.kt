package com.vincentmignot.nudgi.core.export

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.NUDGI_DATABASE_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.ZoneId
import javax.inject.Inject

/** Exports everything Nudgi recorded to a document the user picked. */
fun interface DataExporter {
    suspend fun exportTo(destination: Uri): ExportSummary
}

/**
 * Writes to a document created through the Storage Access Framework, so no storage permission is
 * needed and the user decides where the file goes. A failed or cancelled export deletes the partial
 * document: a truncated zip looks like a valid export until a script chokes on it.
 */
class DocumentDataExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val eventDao: EventDao,
        private val dailyStatsDao: DailyStatsDao,
    ) : DataExporter {
        override suspend fun exportTo(destination: Uri): ExportSummary =
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                try {
                    val output =
                        resolver.openOutputStream(destination, "wt")
                            ?: throw FileNotFoundException("Cannot open $destination")
                    output.use {
                        DataExportWriter().write(it, DaoExportSource(eventDao, dailyStatsDao), exportInfo())
                    }
                } catch (e: Throwable) {
                    runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                    throw e
                }
            }

        private fun exportInfo(): ExportInfo {
            val packageInfo = packageInfo()
            return ExportInfo(
                databaseSchemaVersion = NUDGI_DATABASE_VERSION,
                appVersionName = packageInfo.versionName.orEmpty(),
                appVersionCode = packageInfo.longVersionCode,
                exportedAt = System.currentTimeMillis(),
                timeZone = ZoneId.systemDefault().id,
            )
        }

        private fun packageInfo(): PackageInfo {
            val packageManager = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
        }
    }
