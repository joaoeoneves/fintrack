package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val themeKey = stringPreferencesKey(KEY_THEME)
private val currencyKey = stringPreferencesKey(KEY_CURRENCY)
private val languageKey = stringPreferencesKey(KEY_LANGUAGE)

/**
 * Typed view over the single preferences [DataStore]. Missing or unrecognized stored values read
 * as the [UserPreferences] defaults.
 */
@Singleton
class UserPreferencesStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val data: Flow<UserPreferences> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) emit(emptyPreferences()) else throw e
                }.map { it.toUserPreferences() }

        /** Applies [transform] atomically and returns the resulting preferences. */
        suspend fun update(transform: (UserPreferences) -> UserPreferences): UserPreferences {
            var result = UserPreferences()
            dataStore.edit { prefs ->
                result = transform(prefs.toUserPreferences())
                prefs[themeKey] = result.theme.name
                prefs[currencyKey] = result.currency.name
                prefs[languageKey] = result.language.name
            }
            return result
        }

        private fun Preferences.toUserPreferences(): UserPreferences {
            val defaults = UserPreferences()
            return UserPreferences(
                theme = ThemePreference.entries.find { it.name == this[themeKey] } ?: defaults.theme,
                currency = CurrencyOption.entries.find { it.name == this[currencyKey] } ?: defaults.currency,
                language = AppLanguage.entries.find { it.name == this[languageKey] } ?: defaults.language,
            )
        }
    }
