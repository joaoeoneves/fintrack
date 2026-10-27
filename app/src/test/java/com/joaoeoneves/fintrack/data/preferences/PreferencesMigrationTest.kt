package com.joaoeoneves.fintrack.data.preferences

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PreferencesMigrationTest : PreferencesStoreTestBase() {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun seed(
        file: String,
        key: String,
        value: String,
    ) {
        val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString(key, value)
            .commit()
    }

    private suspend fun read(): UserPreferences {
        val dataStore =
            PreferenceDataStoreFactory.create(
                migrations = preferencesMigrations(context),
                scope = newScope(),
                produceFile = { file },
            )
        return UserPreferencesStore(dataStore).data.first()
    }

    @Test
    fun allLegacyValues_areMigrated() =
        runTest {
            seed("theme_prefs", KEY_THEME, "LIGHT")
            seed("currency_prefs", KEY_CURRENCY, "USD")
            seed("language_prefs", KEY_LANGUAGE, "ENGLISH")

            assertEquals(
                UserPreferences(ThemePreference.LIGHT, CurrencyOption.USD, AppLanguage.ENGLISH),
                read(),
            )
        }

    @Test
    fun migration_removesLegacyKeys() =
        runTest {
            seed("theme_prefs", KEY_THEME, "LIGHT")
            seed("currency_prefs", KEY_CURRENCY, "USD")
            seed("language_prefs", KEY_LANGUAGE, "ENGLISH")

            read()

            listOf("theme_prefs", "currency_prefs", "language_prefs").forEach {
                assertEquals(emptyMap<String, Any?>(), context.getSharedPreferences(it, Context.MODE_PRIVATE).all)
            }
        }

    @Test
    fun noLegacyPrefs_yieldsDefaults() =
        runTest {
            assertEquals(UserPreferences(), read())
        }

    @Test
    fun onlyCurrencySeeded_onlyCurrencyMigrated() =
        runTest {
            seed("currency_prefs", KEY_CURRENCY, "GBP")

            assertEquals(UserPreferences(currency = CurrencyOption.GBP), read())
        }

    @Test
    fun invalidLegacyValue_readsAsDefault() =
        runTest {
            seed("language_prefs", KEY_LANGUAGE, "KLINGON")
            seed("theme_prefs", KEY_THEME, "LIGHT")

            assertEquals(UserPreferences(theme = ThemePreference.LIGHT), read())
        }
}
