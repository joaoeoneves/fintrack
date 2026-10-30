package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")

/** TEMPORARY stub of the Track A contract. */
@Singleton
class AppLockStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED_KEY] ?: false }

        suspend fun setEnabled(enabled: Boolean) {
            dataStore.edit { it[ENABLED_KEY] = enabled }
        }
    }
