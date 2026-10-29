package com.joaoeoneves.fintrack.ui.common

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private const val TAG = "FormSnapshotStore"

/**
 * Keeps a form's editable fields in the [SavedStateHandle] (one JSON string under [key]) so they survive process
 * death. A snapshot that cannot be read is ignored: the form then simply starts from its loaded state.
 */
class FormSnapshotStore<T>(
    private val handle: SavedStateHandle,
    private val key: String,
    private val serializer: KSerializer<T>,
) {
    fun read(): T? =
        handle.get<String>(key)?.let { json ->
            runCatching { Json.decodeFromString(serializer, json) }
                .onFailure { Log.w(TAG, "Dropping unreadable form snapshot", it) }
                .getOrNull()
        }

    fun write(snapshot: T) {
        handle[key] = Json.encodeToString(serializer, snapshot)
    }
}
