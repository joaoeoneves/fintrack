package com.joaoeoneves.fintrack.ui.savings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A deleted savings plan: the [goal] and the monthly [item] it owned (`null` when it had none). */
data class DeletedSavingsPlan(
    val goal: SavingsGoal,
    val item: RecurringItem?,
)

/** Shows the app-level "Plan deleted / Undo" snackbar; the default does nothing (previews, tests). */
val LocalSavingsPlanDeleteUndo = staticCompositionLocalOf<(DeletedSavingsPlan) -> Unit> { {} }

/** Hands each plan emitted by [deleted] to the app-level undo snackbar, then calls [onDeleted]. */
@Composable
fun DeletedSavingsPlanEffect(
    deleted: Flow<DeletedSavingsPlan>,
    onDeleted: () -> Unit,
) {
    val showDeleted = LocalSavingsPlanDeleteUndo.current
    LaunchedEffect(Unit) {
        deleted.collectLatest {
            showDeleted(it)
            onDeleted()
        }
    }
}

/** Restores a plan whose deletion the user undid from the app-level snackbar. */
@HiltViewModel
class SavingsUndoViewModel
    @Inject
    constructor(
        private val goals: GoalRepository,
        private val recurring: RecurringRepository,
    ) : ViewModel() {
        /** Re-creates the monthly item first (same id, so its posted expenses count again), then the goal. */
        fun restore(deleted: DeletedSavingsPlan) {
            viewModelScope.launch {
                val restored = deleted.item?.let { recurring.saveRecurring(it) }
                if (restored == null || restored.isSuccess) goals.saveGoal(deleted.goal)
            }
        }
    }
