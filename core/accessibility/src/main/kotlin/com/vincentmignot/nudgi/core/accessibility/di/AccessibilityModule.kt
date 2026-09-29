package com.vincentmignot.nudgi.core.accessibility.di

import com.vincentmignot.nudgi.core.accessibility.AccessibilityActions
import com.vincentmignot.nudgi.core.accessibility.ServiceAccessibilityActions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityModule {
    @Binds
    abstract fun bindAccessibilityActions(impl: ServiceAccessibilityActions): AccessibilityActions
}
