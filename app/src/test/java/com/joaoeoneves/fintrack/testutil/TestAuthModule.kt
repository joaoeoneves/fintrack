package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.FakeAuthRepository
import com.joaoeoneves.fintrack.di.AuthModule
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Replaces the Firebase sign-in with a signed-out in-memory fake for full-app JVM tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AuthModule::class])
object TestAuthModule {
    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = FakeAuthRepository()
}
