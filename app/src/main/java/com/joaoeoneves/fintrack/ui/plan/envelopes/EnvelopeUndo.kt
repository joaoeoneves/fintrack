package com.joaoeoneves.fintrack.ui.plan.envelopes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A deleted envelope: [edit] re-creates it with its members, [position] is its place in the list. */
data class DeletedEnvelope(
    val edit: EnvelopeEdit,
    val position: Int,
)

/** Shows the app-level "Envelope deleted / Undo" snackbar; the default does nothing (previews, tests). */
val LocalEnvelopeDeleteUndo = staticCompositionLocalOf<(DeletedEnvelope) -> Unit> { {} }

/** Hands each envelope emitted by [deleted] to the app-level undo snackbar, then calls [onDeleted]. */
@Composable
fun DeletedEnvelopeEffect(
    deleted: Flow<DeletedEnvelope>,
    onDeleted: () -> Unit,
) {
    val showDeleted = LocalEnvelopeDeleteUndo.current
    LaunchedEffect(Unit) {
        deleted.collectLatest {
            showDeleted(it)
            onDeleted()
        }
    }
}

/** Restores an envelope whose deletion the user undid from the app-level snackbar. */
@HiltViewModel
class EnvelopeUndoViewModel
    @Inject
    constructor(
        private val repository: EnvelopeRepository,
    ) : ViewModel() {
        /** Re-creates [deleted] with its categories and moves it back to its old position. */
        fun restore(deleted: DeletedEnvelope) {
            viewModelScope.launch {
                val id = repository.saveEnvelope(deleted.edit).getOrNull() ?: return@launch
                val ids =
                    repository
                        .observeEnvelopes()
                        .first()
                        .map { it.id }
                        .filter { it != id }
                repository.reorderEnvelopes(ids.toMutableList().apply { add(deleted.position.coerceIn(0, size), id) })
            }
        }
    }
