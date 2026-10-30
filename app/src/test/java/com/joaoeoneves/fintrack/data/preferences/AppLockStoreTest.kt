package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockStoreTest : PreferencesStoreTestBase() {
    private val dataStore = newDataStore(newScope())
    private val store = AppLockStore(dataStore)

    @Test
    fun default_isOff() = runTest { assertEquals(false, store.enabled.first()) }

    @Test
    fun setEnabled_roundTrips() =
        runTest {
            store.setEnabled(true)
            assertEquals(true, store.enabled.first())

            store.setEnabled(false)
            assertEquals(false, store.enabled.first())
        }

    @Test
    fun userPreferencesUpdate_keepsLockFlag() =
        runTest {
            store.setEnabled(true)
            val prefs = UserPreferencesStore(dataStore)

            prefs.update { it.copy(theme = ThemePreference.LIGHT, language = AppLanguage.ENGLISH) }

            assertEquals(true, store.enabled.first())
            assertEquals(true, dataStore.data.first()[booleanPreferencesKey("app_lock_enabled")])
        }
}
