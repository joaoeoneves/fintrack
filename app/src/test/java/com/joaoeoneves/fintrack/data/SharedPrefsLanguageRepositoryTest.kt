package com.joaoeoneves.fintrack.data

import android.app.Application
import android.content.Context
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [SharedPrefsLanguageRepository], backed by a real (Robolectric-shadowed)
 * `SharedPreferences` instance, mirroring [SharedPrefsCurrencyRepositoryTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPrefsLanguageRepositoryTest {
    private fun context() = RuntimeEnvironment.getApplication()

    @Test
    fun observeLanguage_nothingStoredYet_defaultsToPortuguese() =
        runBlocking {
            assertEquals(AppLanguage.PORTUGUESE, SharedPrefsLanguageRepository(context()).observeLanguage().first())
        }

    @Test
    fun setLanguage_toEachOption_persistsAndObserveEmitsUpdate() =
        runBlocking {
            val repo = SharedPrefsLanguageRepository(context())

            for (language in AppLanguage.entries) {
                repo.setLanguage(language)
                assertEquals(language, repo.observeLanguage().first())
            }
        }

    @Test
    fun setLanguage_survivesFreshRepositoryInstance_simulatingAppRestart() =
        runBlocking {
            val ctx = context()
            SharedPrefsLanguageRepository(ctx).setLanguage(AppLanguage.ENGLISH)

            assertEquals(AppLanguage.ENGLISH, SharedPrefsLanguageRepository(ctx).observeLanguage().first())
        }

    @Test
    fun setLanguage_writesEnumNameToPrefs() =
        runBlocking {
            val ctx = context()
            SharedPrefsLanguageRepository(ctx).setLanguage(AppLanguage.SYSTEM)

            val stored = ctx.getSharedPreferences("language_prefs", Context.MODE_PRIVATE).getString("language", null)

            assertEquals("SYSTEM", stored)
        }

    @Test
    fun observeLanguage_unrecognizedStoredValue_defaultsToPortuguese() =
        runBlocking {
            val ctx = context()
            ctx
                .getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("language", "KLINGON")
                .commit()

            assertEquals(AppLanguage.PORTUGUESE, SharedPrefsLanguageRepository(ctx).observeLanguage().first())
        }
}
