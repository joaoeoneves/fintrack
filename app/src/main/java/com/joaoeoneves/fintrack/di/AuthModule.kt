package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.FirebaseAuthRepository
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Kept apart from [RepositoryModule] so JVM screen tests can replace just the sign-in binding. */
@Module
@InstallIn(SingletonComponent::class)
interface AuthModule {
    @Binds
    fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
