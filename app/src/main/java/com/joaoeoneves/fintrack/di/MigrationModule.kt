package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.migration.AccountMigrator
import com.joaoeoneves.fintrack.data.migration.FirestoreAccountMigrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface MigrationModule {
    @Binds
    fun bindAccountMigrator(impl: FirestoreAccountMigrator): AccountMigrator
}
