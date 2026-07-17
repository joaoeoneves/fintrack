package com.joaoeoneves.fintrack.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "language_prefs"
private const val KEY_LANGUAGE = "language"

/**
 * [LanguageRepository] backed by [android.content.SharedPreferences], storing the [AppLanguage]
 * enum name as a string. Absent/unrecognized values default to [AppLanguage.PORTUGUESE] (the
 * first-install default).
 *
 * [setLanguage] both persists the preference and applies it app-wide via
 * [AppCompatDelegate.setApplicationLocales], which drives `stringResource()`/date-formatting
 * locale resolution across the whole app by updating the `Configuration` used to recreate the
 * current activity.
 *
 * Scoped as a singleton because it caches state in [languageFlow]; a second instance would seed
 * its own copy from disk and never observe writes made through the first instance.
 */
@Singleton
class SharedPrefsLanguageRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : LanguageRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private val languageFlow = MutableStateFlow(readStoredLanguage())

        override fun observeLanguage(): Flow<AppLanguage> = languageFlow

        override fun applyPersistedLocale() {
            AppCompatDelegate.setApplicationLocales(languageFlow.value.toLocaleListCompat())
        }

        override suspend fun setLanguage(language: AppLanguage) {
            prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
            languageFlow.value = language
            AppCompatDelegate.setApplicationLocales(language.toLocaleListCompat())
        }

        private fun readStoredLanguage(): AppLanguage {
            val stored = prefs.getString(KEY_LANGUAGE, null) ?: return AppLanguage.PORTUGUESE
            return AppLanguage.entries.find { it.name == stored } ?: AppLanguage.PORTUGUESE
        }

        private fun AppLanguage.toLocaleListCompat(): LocaleListCompat =
            when (this) {
                AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                AppLanguage.PORTUGUESE -> LocaleListCompat.forLanguageTags("pt-PT")
            }
    }
