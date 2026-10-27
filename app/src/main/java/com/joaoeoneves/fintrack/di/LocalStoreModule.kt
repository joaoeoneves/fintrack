package com.joaoeoneves.fintrack.di

import android.content.Context
import com.joaoeoneves.fintrack.data.local.IdGenerator
import com.joaoeoneves.fintrack.data.local.RandomIdGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Singleton

private const val GUEST_DATA_FILE_NAME = "guest-data.json"

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalStoreModule {
    @Binds
    abstract fun bindIdGenerator(impl: RandomIdGenerator): IdGenerator

    companion object {
        @Provides
        @Singleton
        @GuestDataFile
        fun provideGuestDataFile(
            @ApplicationContext context: Context,
        ): File = File(context.filesDir, GUEST_DATA_FILE_NAME)

        @OptIn(ExperimentalSerializationApi::class)
        @Provides
        @Singleton
        fun provideLocalJson(): Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            }
    }
}
