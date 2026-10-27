package com.joaoeoneves.fintrack.data.local

import android.util.Log
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.di.GuestDataFile
import com.joaoeoneves.fintrack.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalDataStore"
private const val MIGRATED_BACKUP_NAME = "guest-data.migrated.json"

/**
 * The guest-mode data set: one JSON file, loaded lazily, held in memory and rewritten atomically
 * (tmp file, fsync, rename) on every change. New state is published to [data] only after the write
 * succeeded, so observers never see data that is not on disk. A corrupt or newer-schema file is
 * moved aside (never deleted) and the store starts empty.
 */
@Singleton
class LocalDataStore
    @Inject
    constructor(
        @param:GuestDataFile private val file: File,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val json: Json,
    ) {
        private val mutex = Mutex()
        private val state = MutableStateFlow<LocalDataDocument?>(null)
        private val tmpFile get() = File(file.path + ".tmp")

        /** The current document; emits the loaded state first, then every successfully written change. */
        val data: Flow<LocalDataDocument> =
            flow {
                if (state.value == null) mutex.withLock { loadLocked() }
                emitAll(state.filterNotNull())
            }

        /**
         * Applies [transform] (current document to new document plus a result) under the store lock and
         * persists it with `revision + 1`. A transform that leaves the content unchanged writes nothing.
         * Any failure (a thrown [IllegalArgumentException] from validation, an [IOException]) yields
         * [Result.failure] and leaves the stored data untouched; cancellation is rethrown.
         */
        suspend fun <T> update(transform: (LocalDataDocument) -> Pair<LocalDataDocument, T>): Result<T> =
            runCatchingCancellable {
                mutex.withLock {
                    val current = loadLocked()
                    val (changed, result) = transform(current)
                    if (changed.copy(revision = current.revision, schemaVersion = current.schemaVersion) != current) {
                        val next = changed.copy(schemaVersion = LOCAL_SCHEMA_VERSION, revision = current.revision + 1)
                        persistLocked(next)
                    }
                    result
                }
            }

        /** Replaces the whole data set with [doc] (restore from a backup). */
        suspend fun replaceAll(doc: LocalDataDocument): Result<Unit> = update { doc to Unit }

        /**
         * Copies the current file to `guest-data.migrated.json` (one slot, overwritten) and then writes
         * an empty data set, for after the data was moved to an account.
         */
        suspend fun clearWithBackup(): Result<Unit> =
            runCatchingCancellable {
                mutex.withLock {
                    val current = loadLocked()
                    // An empty document has nothing worth keeping; skipping it protects an earlier backup.
                    val hasData = current.copy(revision = 0) != LocalDataDocument()
                    if (hasData && file.exists()) {
                        withContext(dispatcher) {
                            file.copyTo(File(file.parentFile, MIGRATED_BACKUP_NAME), overwrite = true)
                        }
                    }
                    persistLocked(LocalDataDocument(revision = current.revision + 1))
                }
            }

        private suspend fun loadLocked(): LocalDataDocument =
            state.value ?: withContext(dispatcher) { readFromDisk() }.also { state.value = it }

        private suspend fun persistLocked(doc: LocalDataDocument) {
            val bytes = json.encodeToString(LocalDataDocument.serializer(), doc).toByteArray(Charsets.UTF_8)
            withContext(dispatcher) { writeAtomically(bytes) }
            state.value = doc
        }

        private fun readFromDisk(): LocalDataDocument {
            tmpFile.delete()
            if (!file.exists()) return LocalDataDocument()
            val doc =
                try {
                    json.decodeFromString(LocalDataDocument.serializer(), file.readText(Charsets.UTF_8))
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Guest data file is corrupt; moving it aside", e)
                    null
                }
            return if (doc != null && doc.schemaVersion <= LOCAL_SCHEMA_VERSION) {
                doc
            } else {
                if (doc != null) Log.w(TAG, "Guest data file has newer schema ${doc.schemaVersion}; moving it aside")
                moveAside()
                LocalDataDocument()
            }
        }

        private fun moveAside() {
            val aside = File(file.parentFile, "guest-data.corrupt-${System.currentTimeMillis()}.json")
            if (!file.renameTo(aside)) {
                // Prefer keeping the original: if it can't be moved, try a copy (the next write replaces it).
                // If both fail the original can still be lost.
                runCatching { file.copyTo(aside, overwrite = true) }
                    .onFailure { Log.w(TAG, "Could not preserve unreadable guest data file", it) }
            }
        }

        private fun writeAtomically(bytes: ByteArray) {
            val tmp = tmpFile
            try {
                FileOutputStream(tmp).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
                if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
            } catch (e: IOException) {
                tmp.delete()
                throw e
            }
        }
    }
