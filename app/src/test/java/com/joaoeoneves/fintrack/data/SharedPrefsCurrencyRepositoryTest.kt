package com.joaoeoneves.fintrack.data

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [SharedPrefsCurrencyRepository], backed by a real (Robolectric-shadowed)
 * `SharedPreferences` instance, mirroring [SharedPrefsThemeRepositoryTest] since the class
 * directly depends on `Context.getSharedPreferences` rather than accepting an injectable
 * `SharedPreferences` seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPrefsCurrencyRepositoryTest {
    private fun context() = RuntimeEnvironment.getApplication()

    @Test
    fun observeCurrency_nothingStoredYet_defaultsToUsd() =
        runBlocking {
            val repo = SharedPrefsCurrencyRepository(context())

            val result = repo.observeCurrency().first()

            assertEquals(CurrencyOption.USD, result)
        }

    @Test
    fun setCurrency_persistsAndObserveEmitsUpdate() =
        runBlocking {
            val repo = SharedPrefsCurrencyRepository(context())

            repo.setCurrency(CurrencyOption.EUR)

            assertEquals(CurrencyOption.EUR, repo.observeCurrency().first())
        }

    @Test
    fun setCurrency_toEachOption_persistsAndObserveEmitsUpdate() =
        runBlocking {
            val repo = SharedPrefsCurrencyRepository(context())

            for (option in CurrencyOption.entries) {
                repo.setCurrency(option)
                assertEquals(option, repo.observeCurrency().first())
            }
        }

    @Test
    fun setCurrency_survivesFreshRepositoryInstance_simulatingAppRestart() =
        runBlocking {
            val ctx = context()
            val firstInstance = SharedPrefsCurrencyRepository(ctx)
            firstInstance.setCurrency(CurrencyOption.JPY)

            // A brand-new repository instance backed by the same underlying SharedPreferences file
            // (same Context/package) simulates a process death + relaunch.
            val secondInstance = SharedPrefsCurrencyRepository(ctx)

            assertEquals(CurrencyOption.JPY, secondInstance.observeCurrency().first())
        }

    @Test
    fun setCurrency_backToUsdExplicitly_isPersistedLikeAnyOtherValue() =
        runBlocking {
            val ctx = context()
            val repo = SharedPrefsCurrencyRepository(ctx)
            repo.setCurrency(CurrencyOption.GBP)

            repo.setCurrency(CurrencyOption.USD)

            val freshInstance = SharedPrefsCurrencyRepository(ctx)
            assertEquals(CurrencyOption.USD, freshInstance.observeCurrency().first())
        }

    @Test
    fun readStoredCurrency_unrecognizedStoredString_fallsBackToUsd() =
        runBlocking {
            val ctx = context()
            // Simulate a corrupted/unrecognized stored value (e.g. from a removed enum constant in
            // a future version) written directly via the same prefs file/key this repository uses.
            ctx
                .getSharedPreferences("currency_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("currency", "NOT_A_REAL_CURRENCY")
                .apply()

            val repo = SharedPrefsCurrencyRepository(ctx)

            assertEquals(CurrencyOption.USD, repo.observeCurrency().first())
        }

    @Test
    fun readStoredCurrency_emptyStoredString_fallsBackToUsd() =
        runBlocking {
            val ctx = context()
            ctx
                .getSharedPreferences("currency_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("currency", "")
                .apply()

            val repo = SharedPrefsCurrencyRepository(ctx)

            assertEquals(CurrencyOption.USD, repo.observeCurrency().first())
        }
}
