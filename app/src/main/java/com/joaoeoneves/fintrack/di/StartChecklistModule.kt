package com.joaoeoneves.fintrack.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** How long Home waits for the plan data to settle before deciding whether to show "Get started". */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StartChecklistSettleMillis

private const val SETTLE_MILLIS = 2_000L

@Module
@InstallIn(SingletonComponent::class)
object StartChecklistModule {
    @Provides
    @StartChecklistSettleMillis
    fun provideSettleMillis(): Long = SETTLE_MILLIS
}
