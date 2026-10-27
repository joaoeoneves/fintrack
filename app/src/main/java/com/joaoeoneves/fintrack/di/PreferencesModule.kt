package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.preferences.FirestorePreferencesSync
import com.joaoeoneves.fintrack.data.preferences.PreferencesSync
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesRepository
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PreferencesModule {
    @Binds
    @Singleton
    fun bindThemeRepository(impl: UserPreferencesRepository): ThemeRepository

    @Binds
    @Singleton
    fun bindCurrencyRepository(impl: UserPreferencesRepository): CurrencyRepository

    @Binds
    @Singleton
    fun bindLanguageRepository(impl: UserPreferencesRepository): LanguageRepository

    @Binds
    @Singleton
    fun bindPreferencesSync(impl: FirestorePreferencesSync): PreferencesSync
}
