package com.vincentmignot.nudgi.core.export.di

import com.vincentmignot.nudgi.core.export.DataExporter
import com.vincentmignot.nudgi.core.export.DataImporter
import com.vincentmignot.nudgi.core.export.DocumentDataExporter
import com.vincentmignot.nudgi.core.export.DocumentDataImporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    abstract fun bindDataExporter(impl: DocumentDataExporter): DataExporter

    @Binds
    abstract fun bindDataImporter(impl: DocumentDataImporter): DataImporter
}
