package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joaoeoneves.fintrack.domain.model.MotionPreference
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.testutil.FakePreferencesSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesMotionTest : PreferencesStoreTestBase() {
    private val dataStore = newDataStore(newScope())
    private val store = UserPreferencesStore(dataStore)
    private val sync = FakePreferencesSync()
    private val repo = UserPreferencesRepository(store, sync)

    @Test
    fun default_isSystem() =
        runTest {
            assertEquals(MotionPreference.SYSTEM, repo.observeMotionPreference().first())
        }

    @Test
    fun roundTrip_persistsEachValue_andDoesNotPushToSync() =
        runTest {
            MotionPreference.entries.forEach {
                repo.setMotionPreference(it)
                assertEquals(it, repo.observeMotionPreference().first())
                assertEquals(it, store.data.first().reducedMotion)
            }
            assertEquals(emptyList<Any>(), sync.pushes)
        }

    @Test
    fun unknownStoredValue_fallsBackToSystem() =
        runTest {
            dataStore.edit { it[stringPreferencesKey(KEY_REDUCED_MOTION)] = "BOGUS" }

            assertEquals(MotionPreference.SYSTEM, store.data.first().reducedMotion)
        }

    @Test
    fun update_otherField_keepsMotion() =
        runTest {
            repo.setMotionPreference(MotionPreference.OFF)
            repo.setThemePreference(ThemePreference.LIGHT)

            assertEquals(MotionPreference.OFF, repo.observeMotionPreference().first())
        }
}
