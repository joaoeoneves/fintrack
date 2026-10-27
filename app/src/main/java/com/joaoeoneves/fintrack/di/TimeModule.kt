package com.joaoeoneves.fintrack.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides the wall [Clock] ViewModels read "now" and the time zone from, so tests can inject a
 * fixed clock instead of depending on the real date.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
