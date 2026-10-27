package com.joaoeoneves.fintrack.data.migration

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MigrationOfferStore"
private val HANDLED_REVISION_KEY = longPreferencesKey("local_migration_handled_revision")

/** Remembers the local-data revision the user already decided about (migrated, kept or declined). */
@Singleton
class MigrationOfferStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /** The handled revision, or `null` when the user never decided. */
        val handledRevision: Flow<Long?> =
            dataStore.data
                .catch { e ->
                    if (e !is IOException) throw e
                    Log.w(TAG, "Could not read the handled migration revision; assuming none", e)
                    emit(emptyPreferences())
                }.map { it[HANDLED_REVISION_KEY] }

        /** Persists [revision] as handled; a disk failure is returned as [Result.failure]. */
        suspend fun markHandled(revision: Long): Result<Unit> =
            try {
                dataStore.edit { it[HANDLED_REVISION_KEY] = revision }
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
    }
