package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesStoreTest : PreferencesStoreTestBase() {
    private val scope = newScope()
    private val dataStore = newDataStore(scope)
    private val store = UserPreferencesStore(dataStore)

    @Test
    fun data_onFreshFile_emitsDefaults() =
        runTest {
            assertEquals(
                UserPreferences(ThemePreference.DARK, CurrencyOption.EUR, AppLanguage.SYSTEM),
                store.data.first(),
            )
        }

    @Test
    fun update_persistsEachFieldAndReturnsNewValue() =
        runTest {
            val result =
                store.update {
                    it.copy(ThemePreference.LIGHT, CurrencyOption.USD, AppLanguage.ENGLISH)
                }

            val expected = UserPreferences(ThemePreference.LIGHT, CurrencyOption.USD, AppLanguage.ENGLISH)
            assertEquals(expected, result)
            assertEquals(expected, store.data.first())
        }

    @Test
    fun update_singleField_leavesOthersAtCurrentValues() =
        runTest {
            store.update { it.copy(currency = CurrencyOption.GBP) }

            assertEquals(
                UserPreferences(ThemePreference.DARK, CurrencyOption.GBP, AppLanguage.SYSTEM),
                store.data.first(),
            )
        }

    @Test
    fun update_transformReceivesPreviousValue() =
        runTest {
            store.update { it.copy(theme = ThemePreference.SYSTEM) }

            val seen = mutableListOf<ThemePreference>()
            store.update {
                seen += it.theme
                it
            }

            assertEquals(listOf(ThemePreference.SYSTEM), seen)
        }

    @Test
    fun secondStoreOnSameFile_readsPersistedValues() =
        runTest {
            store.update { UserPreferences(ThemePreference.LIGHT, CurrencyOption.JPY, AppLanguage.SYSTEM) }
            scope.coroutineContext[Job]!!.cancelAndJoin()

            val second = UserPreferencesStore(newDataStore(newScope()))

            assertEquals(
                UserPreferences(ThemePreference.LIGHT, CurrencyOption.JPY, AppLanguage.SYSTEM),
                second.data.first(),
            )
        }

    @Test
    fun data_unknownRawValues_readAsDefaults() =
        runTest {
            writeRaw("KLINGON")

            assertEquals(UserPreferences(), store.data.first())
        }

    @Test
    fun data_emptyRawValues_readAsDefaults() =
        runTest {
            writeRaw("")

            assertEquals(UserPreferences(), store.data.first())
        }

    @Test
    fun data_lowercaseRawValue_isNotRecognized() =
        runTest {
            writeRaw("light")

            assertEquals(UserPreferences(), store.data.first())
        }

    private suspend fun writeRaw(value: String) {
        dataStore.edit {
            it[stringPreferencesKey(KEY_THEME)] = value
            it[stringPreferencesKey(KEY_CURRENCY)] = value
            it[stringPreferencesKey(KEY_LANGUAGE)] = value
        }
    }
}
