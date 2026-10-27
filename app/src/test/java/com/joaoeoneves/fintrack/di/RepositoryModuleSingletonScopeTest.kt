package com.joaoeoneves.fintrack.di

import android.content.Context
import androidx.datastore.core.DataStore
import com.joaoeoneves.fintrack.data.preferences.FirestorePreferencesSync
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesRepository
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

/**
 * Regression guard for a bug that shipped once: a preference repository that loses its
 * `@Singleton` scope makes Hilt hand each injection point (e.g. `MainActivity`'s
 * `CurrencyViewModel`/`ThemeViewModel` vs. `SettingsViewModel`) a separate instance, so a write
 * through one never reaches another's observers (the live Dashboard would ignore a Settings
 * change until relaunch). See the header comment in `.maestro/settings.yaml`.
 *
 * It is worse with DataStore: there must be exactly one DataStore instance per file, because a
 * second instance over the same file throws ("There are multiple DataStores active for the same
 * file"). So [DataStoreModule.providePreferencesDataStore], [UserPreferencesStore] and
 * [UserPreferencesRepository] must all stay `@Singleton`, as must the binds in [PreferencesModule].
 *
 * Rather than stand up a Hilt test component, this asserts the cheap structural precondition:
 * `@Singleton` on each class and binding/provider method.
 */
class RepositoryModuleSingletonScopeTest {
    @Test
    fun userPreferencesRepository_classIsAnnotatedSingleton() {
        assertSingleton(UserPreferencesRepository::class.java.isSingleton(), "UserPreferencesRepository")
    }

    @Test
    fun userPreferencesStore_classIsAnnotatedSingleton() {
        assertSingleton(UserPreferencesStore::class.java.isSingleton(), "UserPreferencesStore")
    }

    @Test
    fun firestorePreferencesSync_classIsAnnotatedSingleton() {
        assertSingleton(FirestorePreferencesSync::class.java.isSingleton(), "FirestorePreferencesSync")
    }

    @Test
    fun bindCurrencyRepository_takesUserPreferencesRepositoryAndIsSingleton() {
        assertBindSingleton("bindCurrencyRepository")
    }

    @Test
    fun bindThemeRepository_takesUserPreferencesRepositoryAndIsSingleton() {
        assertBindSingleton("bindThemeRepository")
    }

    @Test
    fun bindLanguageRepository_takesUserPreferencesRepositoryAndIsSingleton() {
        assertBindSingleton("bindLanguageRepository")
    }

    @Test
    fun bindPreferencesSync_takesFirestoreImplAndIsSingleton() {
        val method =
            PreferencesModule::class.java.getDeclaredMethod(
                "bindPreferencesSync",
                FirestorePreferencesSync::class.java,
            )
        assertSingleton(method.isAnnotationPresent(Singleton::class.java), "bindPreferencesSync")
    }

    @Test
    fun providePreferencesDataStore_isSingleton() {
        val method =
            DataStoreModule::class.java.getDeclaredMethod(
                "providePreferencesDataStore",
                Context::class.java,
            )
        assertTrue(DataStore::class.java.isAssignableFrom(method.returnType))
        assertSingleton(method.isAnnotationPresent(Singleton::class.java), "providePreferencesDataStore")
    }

    private fun Class<*>.isSingleton() = isAnnotationPresent(Singleton::class.java)

    private fun assertBindSingleton(name: String) {
        val method =
            PreferencesModule::class.java.getDeclaredMethod(name, UserPreferencesRepository::class.java)
        assertSingleton(method.isAnnotationPresent(Singleton::class.java), "PreferencesModule.$name")
    }

    private fun assertSingleton(
        present: Boolean,
        what: String,
    ) {
        assertTrue("$what must be @Singleton -- see class doc for why", present)
    }
}
