package com.joaoeoneves.fintrack.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestoreDataResetRepository
import com.joaoeoneves.fintrack.data.local.LOCAL_SCHEMA_VERSION
import com.joaoeoneves.fintrack.data.local.LocalDataResetRepository
import com.joaoeoneves.fintrack.data.local.LocalDataStore
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.data.preferences.PreferenceField
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesRepository
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import com.joaoeoneves.fintrack.testutil.FakePreferencesSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** [SessionDataResetRepository] as a guest: wipes the local store, resets preferences, never touches remote. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDataResetRepositoryGuestTest : LocalStoreTestBase() {
    private val sync = FakePreferencesSync()
    private val remote = CountingLazy<FirestoreDataResetRepository>(null)

    private fun prefsStore() =
        UserPreferencesStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
                produceFile = { File(tmp.root, "prefs.preferences_pb") },
            ),
        )

    private fun repo(
        store: LocalDataStore,
        prefs: UserPreferencesRepository,
    ) = SessionDataResetRepository(
        FakeSessionRepository(initialSession = Session.Guest),
        remote,
        LocalDataResetRepository(store),
        prefs,
    )

    @Test
    fun guest_wipesLocalStoreAndResetsPreferences_withoutResolvingRemote() =
        runStoreTest {
            val store = newStore()
            store.update { it.copy(monthSettings = listOf(LocalMonthSettings("2026-10", 2))) to Unit }
            val prefs = UserPreferencesRepository(prefsStore(), sync)
            prefs.setThemePreference(ThemePreference.LIGHT)
            prefs.setLanguage(AppLanguage.ENGLISH)
            sync.pushes.clear()

            val result = repo(store, prefs).deleteAllData()

            assertTrue(result.isSuccess)
            assertEquals(0, remote.gets)
            assertTrue(
                store.data
                    .first()
                    .monthSettings
                    .isEmpty(),
            )
            assertEquals(LOCAL_SCHEMA_VERSION, store.data.first().schemaVersion)
            val defaults = UserPreferences()
            assertEquals(defaults.theme, prefs.observeThemePreference().first())
            assertEquals(defaults.language, prefs.observeLanguage().first())
            assertEquals(
                setOf(PreferenceField.THEME, PreferenceField.LANGUAGE),
                sync.pushes.map { it.first }.toSet(),
            )
        }

    @Test
    fun guest_languageReset_toSystem() =
        runStoreTest {
            val prefs = UserPreferencesRepository(prefsStore(), sync)
            prefs.setLanguage(AppLanguage.entries.first { it != AppLanguage.SYSTEM })

            assertTrue(repo(newStore(), prefs).deleteAllData().isSuccess)

            assertEquals(AppLanguage.SYSTEM, prefs.observeLanguage().first())
        }

    @Test
    fun guest_localFailure_isReturnedAsIs_andPreferencesUntouched() =
        runStoreTest {
            val dir = File(tmp.root, "sub").apply { mkdirs() }
            val store = newStore(File(dir, "guest-data.json"))
            store.update { it.copy(monthSettings = listOf(LocalMonthSettings("2026-10", 2))) to Unit }
            dir.deleteRecursively()
            dir.writeText("not a directory")
            val prefs = UserPreferencesRepository(prefsStore(), sync)
            prefs.setThemePreference(ThemePreference.LIGHT)
            sync.pushes.clear()

            val result = repo(store, prefs).deleteAllData()

            assertTrue(result.isFailure)
            assertEquals(ThemePreference.LIGHT, prefs.observeThemePreference().first())
            assertTrue(sync.pushes.isEmpty())
            assertEquals(
                1,
                store.data
                    .first()
                    .monthSettings.size,
            )
        }
}
