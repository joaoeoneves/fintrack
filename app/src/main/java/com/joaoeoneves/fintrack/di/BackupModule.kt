package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.backup.AccountBackupRestorer
import com.joaoeoneves.fintrack.data.backup.AccountRestore
import com.joaoeoneves.fintrack.data.backup.FirestoreServerSync
import com.joaoeoneves.fintrack.data.backup.ServerSync
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface BackupModule {
    @Binds
    fun bindAccountRestore(impl: AccountBackupRestorer): AccountRestore

    @Binds
    fun bindServerSync(impl: FirestoreServerSync): ServerSync
}
