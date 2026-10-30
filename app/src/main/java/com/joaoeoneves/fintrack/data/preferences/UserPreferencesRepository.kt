package com.joaoeoneves.fintrack.data.preferences

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of the theme and language repositories. Each setter
 * writes locally first, then pushes the change to Firestore via [PreferencesSync].
 */
@Singleton
class UserPreferencesRepository
    @Inject
    constructor(
        private val store: UserPreferencesStore,
        private val sync: PreferencesSync,
    ) : ThemeRepository,
        LanguageRepository {
        override fun observeThemePreference(): Flow<ThemePreference> = pick { it.theme }

        override suspend fun setThemePreference(preference: ThemePreference) {
            store.update { it.copy(theme = preference) }
            sync.push(PreferenceField.THEME, preference.name)
        }

        override fun observeLanguage(): Flow<AppLanguage> = pick { it.language }

        override suspend fun setLanguage(language: AppLanguage) {
            store.update { it.copy(language = language) }
            sync.push(PreferenceField.LANGUAGE, language.name)
        }

        private fun <T> pick(f: (UserPreferences) -> T): Flow<T> = store.data.map(f).distinctUntilChanged()
    }
