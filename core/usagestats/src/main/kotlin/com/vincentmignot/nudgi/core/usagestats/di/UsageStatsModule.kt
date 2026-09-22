package com.vincentmignot.nudgi.core.usagestats.di

import com.vincentmignot.nudgi.core.usagestats.SharedPreferencesUsagePollState
import com.vincentmignot.nudgi.core.usagestats.SystemUsageAccessPermissionChecker
import com.vincentmignot.nudgi.core.usagestats.SystemUsageEventsSource
import com.vincentmignot.nudgi.core.usagestats.UsageAccessPermissionChecker
import com.vincentmignot.nudgi.core.usagestats.UsageEventsSource
import com.vincentmignot.nudgi.core.usagestats.UsagePollState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageStatsModule {
    @Binds
    abstract fun bindUsageEventsSource(impl: SystemUsageEventsSource): UsageEventsSource

    @Binds
    abstract fun bindUsagePollState(impl: SharedPreferencesUsagePollState): UsagePollState

    @Binds
    abstract fun bindUsageAccessPermissionChecker(
        impl: SystemUsageAccessPermissionChecker,
    ): UsageAccessPermissionChecker
}
