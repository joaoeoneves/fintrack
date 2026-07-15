package com.joaoeoneves.fintrack.data

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [SharedPrefsThemeRepository], backed by a real (Robolectric-shadowed)
 * `SharedPreferences` instance, since the class directly depends on `Context.getSharedPreferences`
 * rather than accepting an injectable `SharedPreferences` seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPrefsThemeRepositoryTest {
    private fun context() = RuntimeEnvironment.getApplication()

    @Test
    fun observeThemePreference_nothingStoredYet_defaultsToSystem() =
        runBlocking {
            val repo = SharedPrefsThemeRepository(context())

            val result = repo.observeThemePreference().first()

            assertEquals(ThemePreference.SYSTEM, result)
        }

    @Test
    fun setThemePreference_persistsAndObserveEmitsUpdate() =
        runBlocking {
            val repo = SharedPrefsThemeRepository(context())

            repo.setThemePreference(ThemePreference.DARK)

            assertEquals(ThemePreference.DARK, repo.observeThemePreference().first())
        }

    @Test
    fun setThemePreference_toLight_persistsAndObserveEmitsUpdate() =
        runBlocking {
            val repo = SharedPrefsThemeRepository(context())

            repo.setThemePreference(ThemePreference.LIGHT)

            assertEquals(ThemePreference.LIGHT, repo.observeThemePreference().first())
        }

    @Test
    fun setThemePreference_survivesFreshRepositoryInstance_simulatingAppRestart() =
        runBlocking {
            val ctx = context()
            val firstInstance = SharedPrefsThemeRepository(ctx)
            firstInstance.setThemePreference(ThemePreference.DARK)

            // A brand-new repository instance backed by the same underlying SharedPreferences file
            // (same Context/package) simulates a process death + relaunch.
            val secondInstance = SharedPrefsThemeRepository(ctx)

            assertEquals(ThemePreference.DARK, secondInstance.observeThemePreference().first())
        }

    @Test
    fun setThemePreference_backToSystemExplicitly_isPersistedLikeAnyOtherValue() =
        runBlocking {
            val ctx = context()
            val repo = SharedPrefsThemeRepository(ctx)
            repo.setThemePreference(ThemePreference.LIGHT)

            repo.setThemePreference(ThemePreference.SYSTEM)

            val freshInstance = SharedPrefsThemeRepository(ctx)
            assertEquals(ThemePreference.SYSTEM, freshInstance.observeThemePreference().first())
        }

    @Test
    fun readStoredPreference_unrecognizedStoredString_defaultsToSystem() =
        runBlocking {
            val ctx = context()
            // Simulate a corrupted/unrecognized stored value (e.g. from a removed enum constant in
            // a future version) written directly via the same prefs file/key this repository uses.
            ctx
                .getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("theme_preference", "NOT_A_REAL_ENUM_VALUE")
                .apply()

            val repo = SharedPrefsThemeRepository(ctx)

            assertEquals(ThemePreference.SYSTEM, repo.observeThemePreference().first())
        }
}
