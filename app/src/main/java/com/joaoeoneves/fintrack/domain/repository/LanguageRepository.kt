package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow

interface LanguageRepository {
    fun observeLanguage(): Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)

    /**
     * Re-applies the currently persisted (or default) language as the app-wide locale override.
     * `AppCompatDelegate.setApplicationLocales()` is a no-op until at least one `AppCompatActivity`
     * has been created, so this must be called from an Activity's `onCreate()` -- calling it earlier
     * (e.g. from `Application.onCreate()`) silently fails to apply, even though the preference itself
     * is already loaded correctly at that point.
     */
    fun applyPersistedLocale()
}
