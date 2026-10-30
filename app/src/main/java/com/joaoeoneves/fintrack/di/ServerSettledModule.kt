package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.NoopServerSettled
import com.joaoeoneves.fintrack.domain.repository.ServerSettled
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ServerSettledModule {
    @Binds
    fun bindServerSettled(impl: NoopServerSettled): ServerSettled
}
