package com.vincentmignot.nudgi.pipeline

import com.vincentmignot.nudgi.core.accessibility.ForegroundAppListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PipelineModule {
    @Binds
    abstract fun bindForegroundAppListener(impl: RealtimeNudgeTrigger): ForegroundAppListener
}
