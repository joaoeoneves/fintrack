package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.RecurringCost
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.LocalDate

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
     * @property investments the items of SAVING-kind categories (any state), shown under their own heading.
     */
    data class Content(
        val active: List<RecurringRow>,
        val inactive: List<RecurringRow>,
        val investments: List<RecurringRow> = emptyList(),
        val cost: RecurringCost,
    ) : RecurringListUiState

    data class Error(
        val message: UiText,
    ) : RecurringListUiState
}

/** Callbacks of the recurring list, bundled to keep the composables' parameter lists short. */
internal data class RecurringListActions(
    val onOpenEditor: (String?) -> Unit,
    val onDeleteRequested: (String) -> Unit,
    val onRetry: () -> Unit,
    val onOpenIncome: () -> Unit = {},
)
