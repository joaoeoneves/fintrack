package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shows the app-level "Recurring deleted / Undo" snackbar; the default does nothing (previews, tests). */
val LocalRecurringDeleteUndo = staticCompositionLocalOf<(RecurringItem) -> Unit> { {} }

/** Hands each item emitted by [deleted] to the app-level undo snackbar, then calls [onDeleted]. */
@Composable
fun DeletedRecurringEffect(
    deleted: Flow<RecurringItem>,
    onDeleted: () -> Unit,
) {
    val showDeleted = LocalRecurringDeleteUndo.current
    LaunchedEffect(Unit) {
        deleted.collectLatest {
            showDeleted(it)
            onDeleted()
        }
    }
}

/** Restores a recurring item whose deletion the user undid from the app-level snackbar. */
@HiltViewModel
class RecurringUndoViewModel
    @Inject
    constructor(
        private val repository: RecurringRepository,
    ) : ViewModel() {
        /** Saves [item] back under its old id (its past transactions were never touched). */
        fun restore(item: RecurringItem) {
            viewModelScope.launch { repository.saveRecurring(item) }
        }
    }
