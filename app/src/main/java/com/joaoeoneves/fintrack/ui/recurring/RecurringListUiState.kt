package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.LocalDate

private const val MONTHS_PER_YEAR = 12

/** The monthly equivalent of a yearly amount, for display. */
fun monthlyEquivalentCents(annualCents: Long): Long = annualCents / MONTHS_PER_YEAR

/**
 * One list row: the [item], its resolved [category] (expenses only), the next pending
 * occurrence ([nextDue], `null` when there is none) and whether it has [isEnded].
 */
data class RecurringRow(
    val item: RecurringItem,
    val category: Category?,
    val nextDue: LocalDate?,
    val isEnded: Boolean,
)

sealed interface RecurringListUiState {
    data object Loading : RecurringListUiState

    /**
     * @property active items that are on and not ended.
     * @property inactive paused or ended items.
     * @property deleteTarget the item awaiting delete confirmation.
     */
    data class Content(
        val active: List<RecurringRow>,
        val inactive: List<RecurringRow>,
        val annualExpenseCents: Long,
        val deleteTarget: RecurringItem? = null,
    ) : RecurringListUiState

    data class Error(
        val message: UiText,
    ) : RecurringListUiState
}

/** Callbacks of the recurring list, bundled to keep the composables' parameter lists short. */
internal data class RecurringListActions(
    val onOpenEditor: (String?) -> Unit,
    val onTogglePaused: (String) -> Unit,
    val onDeleteRequested: (String) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenIncome: () -> Unit = {},
)
