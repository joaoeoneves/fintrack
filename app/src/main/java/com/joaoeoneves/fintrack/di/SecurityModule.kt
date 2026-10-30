package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.security.DeviceLockChecker
import com.joaoeoneves.fintrack.data.security.ElapsedRealtime
import com.joaoeoneves.fintrack.data.security.KeyguardDeviceLockChecker
import com.joaoeoneves.fintrack.data.security.SystemElapsedRealtime
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** TEMPORARY stub of the Track A contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindDeviceLockChecker(impl: KeyguardDeviceLockChecker): DeviceLockChecker

    companion object {
        @Provides
        fun provideElapsedRealtime(): ElapsedRealtime = SystemElapsedRealtime
    }
}
