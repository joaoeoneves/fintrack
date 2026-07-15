package ptech.joaoe.agenticusage.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ptech.joaoe.agenticusage.domain.model.ThemePreference
import ptech.joaoe.agenticusage.domain.repository.ThemeRepository
import javax.inject.Inject

private const val PREFS_NAME = "theme_prefs"
private const val KEY_THEME_PREFERENCE = "theme_preference"

/**
 * [ThemeRepository] backed by [android.content.SharedPreferences], storing the [ThemePreference]
 * enum name as a string. Absent/unrecognized values default to [ThemePreference.SYSTEM].
 */
class SharedPrefsThemeRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ThemeRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private val themePreferenceFlow = MutableStateFlow(readStoredPreference())

        override fun observeThemePreference(): Flow<ThemePreference> = themePreferenceFlow

        override suspend fun setThemePreference(preference: ThemePreference) {
            prefs.edit().putString(KEY_THEME_PREFERENCE, preference.name).apply()
            themePreferenceFlow.value = preference
        }

        private fun readStoredPreference(): ThemePreference {
            val stored = prefs.getString(KEY_THEME_PREFERENCE, null) ?: return ThemePreference.SYSTEM
            return ThemePreference.entries.find { it.name == stored } ?: ThemePreference.SYSTEM
        }
    }
