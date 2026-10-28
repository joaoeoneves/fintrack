package com.joaoeoneves.fintrack.data.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pauses the background migration and automation syncs while a backup is restored, so none of them writes into
 * data that is half replaced. [hold] raises [restoring] around the restore and always releases it, bumping
 * [generation] so the syncs forget what they attempted and look at the restored data afresh.
 */
@Singleton
class RestoreGate
    @Inject
    constructor() {
        private val holds = MutableStateFlow(0)
        private val _restoring = MutableStateFlow(false)
        private val _generation = MutableStateFlow(0)

        /** Whether a restore is running. */
        val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

        /** Counts the finished restores; changes before [restoring] goes back to false. */
        val generation: StateFlow<Int> = _generation.asStateFlow()

        suspend fun <T> hold(block: suspend () -> T): T {
            holds.update { it + 1 }
            _restoring.value = true
            try {
                return block()
            } finally {
                holds.update { (it - 1).coerceAtLeast(0) }
                // Generation first: an observer sees (restoring, new generation) before (idle, new generation).
                _generation.update { it + 1 }
                _restoring.value = holds.value > 0
            }
        }
    }

/** A value of a sync's input flow with the gate state it was observed under. */
class Gated<T>(
    val value: T,
    val restoring: Boolean,
    val generation: Int,
)

/**
 * Combines this flow with [gate]: the collector gets every change of the data, of whether a restore is running and
 * of the restore generation. A `collectLatest` collector returns early while [Gated.restoring] (which cancels its
 * in-flight work) and clears its last-attempt memory when [Gated.generation] changed.
 */
fun <T> Flow<T>.gatedBy(gate: RestoreGate): Flow<Gated<T>> =
    combine(this, gate.restoring, gate.generation) { value, restoring, generation ->
        Gated(value, restoring, generation)
    }
