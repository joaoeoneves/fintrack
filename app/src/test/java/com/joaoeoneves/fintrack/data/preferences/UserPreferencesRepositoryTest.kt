package com.joaoeoneves.fintrack.data.preferences

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import com.joaoeoneves.fintrack.testutil.FakePreferencesSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesRepositoryTest : PreferencesStoreTestBase() {
    private val store = UserPreferencesStore(newDataStore(newScope()))
    private val sync = FakePreferencesSync()
    private val repo = UserPreferencesRepository(store, sync)

    @Test
    fun defaults_areExposed() =
        runTest {
            assertEquals(ThemePreference.DARK, repo.observeThemePreference().first())
            assertEquals(AppLanguage.SYSTEM, repo.observeLanguage().first())
            assertEquals(emptyList<Any>(), sync.pushes)
        }

    @Test
    fun setTheme_updatesFlowAndPushesAfterLocalWrite() =
        runTest {
            var storedAtPush: UserPreferences? = null
            sync.onPush = { _, _ -> storedAtPush = runBlocking { store.data.first() } }

            repo.setThemePreference(ThemePreference.LIGHT)

            assertEquals(ThemePreference.LIGHT, repo.observeThemePreference().first())
            assertEquals(listOf(PreferenceField.THEME to "LIGHT"), sync.pushes)
            assertEquals(ThemePreference.LIGHT, storedAtPush?.theme)
        }

    @Test
    fun setLanguage_updatesFlowAndPushes() =
        runTest {
            repo.setLanguage(AppLanguage.SYSTEM)

            assertEquals(AppLanguage.SYSTEM, repo.observeLanguage().first())
            assertEquals(listOf(PreferenceField.LANGUAGE to "SYSTEM"), sync.pushes)
        }

    @Test
    fun setter_doesNotChangeOtherFields() =
        runTest {
            repo.setLanguage(AppLanguage.ENGLISH)

            assertEquals(ThemePreference.DARK, repo.observeThemePreference().first())
            assertEquals(AppLanguage.ENGLISH, repo.observeLanguage().first())
        }

    @Test
    fun themeChange_doesNotReEmitLanguage() =
        runTest {
            val emissions = mutableListOf<AppLanguage>()
            val job = repo.observeLanguage().onEach { emissions += it }.launchIn(this)

            repo.setThemePreference(ThemePreference.LIGHT)
            repo.setLanguage(AppLanguage.ENGLISH)
            job.cancel()

            assertEquals(listOf(AppLanguage.SYSTEM, AppLanguage.ENGLISH), emissions)
        }

    @Test
    fun sameValueTwice_pushesEachTimeButEmitsOnce() =
        runTest {
            val emissions = mutableListOf<ThemePreference>()
            val job = repo.observeThemePreference().onEach { emissions += it }.launchIn(this)

            repo.setThemePreference(ThemePreference.LIGHT)
            repo.setThemePreference(ThemePreference.LIGHT)
            job.cancel()

            assertEquals(listOf(ThemePreference.DARK, ThemePreference.LIGHT), emissions)
            assertEquals(2, sync.pushes.size)
        }
}
