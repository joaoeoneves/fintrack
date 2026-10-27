package com.joaoeoneves.fintrack.data.session

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

private const val TAG = "SessionFlagStore"
private val GUEST_KEY = booleanPreferencesKey("session_guest")

/** Persists whether the user chose guest mode, in the shared preferences [DataStore]. */
@Singleton
class SessionFlagStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val isGuest: Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e !is IOException) throw e
                    Log.w(TAG, "Could not read the guest flag; assuming not a guest", e)
                    emit(emptyPreferences())
                }.map { it[GUEST_KEY] ?: false }

        /** Persists the flag; a disk failure is returned as [Result.failure] rather than thrown. */
        suspend fun setGuest(guest: Boolean): Result<Unit> =
            try {
                dataStore.edit { it[GUEST_KEY] = guest }
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
    }
