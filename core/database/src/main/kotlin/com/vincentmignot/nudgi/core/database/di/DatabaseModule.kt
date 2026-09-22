package com.vincentmignot.nudgi.core.database.di

import android.content.Context
import androidx.room.Room
import com.vincentmignot.nudgi.core.database.DailyStatsDao
import com.vincentmignot.nudgi.core.database.EventDao
import com.vincentmignot.nudgi.core.database.NudgiDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATABASE_NAME = "nudgi.db"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideNudgiDatabase(
        @ApplicationContext context: Context,
    ): NudgiDatabase = Room.databaseBuilder(context, NudgiDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun provideEventDao(database: NudgiDatabase): EventDao = database.eventDao()

    @Provides
    fun provideDailyStatsDao(database: NudgiDatabase): DailyStatsDao = database.dailyStatsDao()
}
