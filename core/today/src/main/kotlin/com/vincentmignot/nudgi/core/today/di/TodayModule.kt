package com.vincentmignot.nudgi.core.today.di

import com.vincentmignot.nudgi.core.today.RoomTodayRepository
import com.vincentmignot.nudgi.core.today.TodayRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TodayModule {
    @Binds
    abstract fun bindTodayRepository(impl: RoomTodayRepository): TodayRepository
}
