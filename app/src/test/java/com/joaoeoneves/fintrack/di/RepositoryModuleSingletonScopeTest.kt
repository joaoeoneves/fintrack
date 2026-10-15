package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.SharedPrefsCurrencyRepository
import com.joaoeoneves.fintrack.data.SharedPrefsThemeRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

/**
 * Regression guard for a real bug that shipped once: [SharedPrefsCurrencyRepository] and
 * [SharedPrefsThemeRepository] both cache their current value in an in-memory `StateFlow` seeded
 * once from `SharedPreferences` at construction time. If either class -- or its `@Binds` method in
 * [RepositoryModule] -- loses its `@Singleton` scope, Hilt silently starts handing out a *separate*
 * instance to each injection point (e.g. `MainActivity`'s `CurrencyViewModel`/`ThemeViewModel` vs.
 * `SettingsViewModel`). Each instance still persists writes to disk correctly, but a write made
 * through one instance's `setCurrency`/`setThemePreference` never reaches another instance's
 * `observeCurrency`/`observeThemePreference` collectors -- so switching currency/theme in Settings
 * would visibly do nothing on the live Dashboard until the process was killed and relaunched (a
 * fresh instance would then read the persisted value from disk). See the header comment in
 * `.maestro/settings.yaml` for the end-to-end symptom this caused.
 *
 * This can't be caught by [com.joaoeoneves.fintrack.ui.settings.SettingsViewModelTest] or
 * [com.joaoeoneves.fintrack.data.SharedPrefsCurrencyRepositoryTest] since both construct a single
 * repository instance directly and never exercise Hilt's actual scoping graph. Rather than stand up
 * a full Hilt test component just to prove two injection points resolve to `===` the same instance,
 * this asserts the specific, cheap, structural precondition for that to hold: `@Singleton` present
 * on the implementation class and on its binding method.
 */
class RepositoryModuleSingletonScopeTest {
    @Test
    fun sharedPrefsCurrencyRepository_classIsAnnotatedSingleton() {
        assertTrue(
            "SharedPrefsCurrencyRepository must be @Singleton -- see class doc for why",
            SharedPrefsCurrencyRepository::class.java.isAnnotationPresent(Singleton::class.java),
        )
    }

    @Test
    fun sharedPrefsThemeRepository_classIsAnnotatedSingleton() {
        assertTrue(
            "SharedPrefsThemeRepository must be @Singleton -- see class doc for why",
            SharedPrefsThemeRepository::class.java.isAnnotationPresent(Singleton::class.java),
        )
    }

    @Test
    fun bindCurrencyRepository_bindingMethodIsAnnotatedSingleton() {
        val method =
            RepositoryModule::class.java.getDeclaredMethod(
                "bindCurrencyRepository",
                SharedPrefsCurrencyRepository::class.java,
            )

        assertTrue(
            "RepositoryModule.bindCurrencyRepository must be @Singleton -- a non-singleton " +
                "@Binds here means MainActivity's CurrencyViewModel and SettingsViewModel each get " +
                "their own repository instance, so a currency change in Settings silently stops " +
                "reaching the live Dashboard",
            method.isAnnotationPresent(Singleton::class.java),
        )
    }

    @Test
    fun bindThemeRepository_bindingMethodIsAnnotatedSingleton() {
        val method =
            RepositoryModule::class.java.getDeclaredMethod(
                "bindThemeRepository",
                SharedPrefsThemeRepository::class.java,
            )

        assertTrue(
            "RepositoryModule.bindThemeRepository must be @Singleton -- a non-singleton @Binds " +
                "here means MainActivity's ThemeViewModel and SettingsViewModel each get their own " +
                "repository instance, so a theme change in Settings silently stops repainting the " +
                "live app",
            method.isAnnotationPresent(Singleton::class.java),
        )
    }
}
