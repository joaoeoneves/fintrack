package com.joaoeoneves.fintrack.data.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StartChecklistStore"
private val STATE_KEY = stringPreferencesKey("start_checklist_state")

/** Whether Home's "Get started" card is shown, was dismissed, or was never needed. */
enum class StartChecklistState {
    /** Shown until complete or dismissed. */
    ACTIVE,

    /** The user dismissed it. */
    DISMISSED,

    /** The account was already set up when first seen, so it is never shown. */
    RETIRED,
}

/** Persists the [StartChecklistState] device-locally in the shared preferences [DataStore]; absent means undecided. */
@Singleton
class StartChecklistStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /** The stored state, or `null` while undecided. */
        val state: Flow<StartChecklistState?> =
            dataStore.data
                .catch { e ->
                    if (e !is IOException) throw e
                    Log.w(TAG, "Could not read the checklist state", e)
                    emit(emptyPreferences())
                }.map { prefs ->
                    prefs[STATE_KEY]?.let { name -> StartChecklistState.entries.firstOrNull { it.name == name } }
                }

        suspend fun set(state: StartChecklistState) {
            try {
                dataStore.edit { it[STATE_KEY] = state.name }
            } catch (e: IOException) {
                Log.w(TAG, "Could not persist the checklist state", e)
            }
        }

        /** Forgets the decision (after "Delete all data") so a fresh start shows the checklist again. */
        suspend fun clear() {
            try {
                dataStore.edit { it.remove(STATE_KEY) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not clear the checklist state", e)
            }
        }
    }
