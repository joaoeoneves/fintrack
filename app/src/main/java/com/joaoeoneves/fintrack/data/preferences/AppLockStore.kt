package com.joaoeoneves.fintrack.data.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppLockStore"
private val ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")

/**
 * Persists whether the app lock is on, device-locally in the shared preferences [DataStore].
 *
 * Device-only: never synced to Firestore, never part of a backup, and not cleared by "Delete all data" or
 * sign-out, since it protects this device rather than the account's data.
 */
@Singleton
class AppLockStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /** Whether the lock is enabled; absent (or unreadable) means off. */
        val enabled: Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e !is IOException) throw e
                    Log.w(TAG, "Could not read the app lock setting", e)
                    emit(emptyPreferences())
                }.map { prefs -> prefs[ENABLED_KEY] ?: false }

        suspend fun setEnabled(enabled: Boolean) {
            try {
                dataStore.edit { it[ENABLED_KEY] = enabled }
            } catch (e: IOException) {
                Log.w(TAG, "Could not persist the app lock setting", e)
            }
        }
    }
