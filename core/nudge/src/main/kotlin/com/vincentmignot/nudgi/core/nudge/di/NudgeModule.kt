package com.vincentmignot.nudgi.core.nudge.di

import com.vincentmignot.nudgi.core.nudge.AndroidNudgeNotifier
import com.vincentmignot.nudgi.core.nudge.CategoryWatchedApps
import com.vincentmignot.nudgi.core.nudge.HoldoutDraw
import com.vincentmignot.nudgi.core.nudge.NudgeConfig
import com.vincentmignot.nudgi.core.nudge.NudgeNotifier
import com.vincentmignot.nudgi.core.nudge.RandomHoldoutDraw
import com.vincentmignot.nudgi.core.nudge.WatchedApps
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NudgeModule {
    @Binds
    abstract fun bindWatchedApps(impl: CategoryWatchedApps): WatchedApps

    @Binds
    abstract fun bindNudgeNotifier(impl: AndroidNudgeNotifier): NudgeNotifier

    @Binds
    abstract fun bindHoldoutDraw(impl: RandomHoldoutDraw): HoldoutDraw

    companion object {
        @Provides
        fun provideNudgeConfig(): NudgeConfig = NudgeConfig()
    }
}
