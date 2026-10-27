package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface MonthlyPlanUiState {
    data object Loading : MonthlyPlanUiState

    /**
     * @property incomeLines the expected-income lines, in display order.
     * @property allocations one row per category with its planned limit (or none).
     * @property summary the plan's totals; it has no actual income or spend side, only planned amounts.
     * @property canAddIncomeLine whether the line cap has not been reached yet.
     * @property editor the income-line dialog, or `null` when it is closed.
     */
    data class Content(
        val incomeLines: List<PlannedIncome>,
        val allocations: List<CategoryBudget>,
        val summary: MonthlyPlanSummary,
        val canAddIncomeLine: Boolean,
        val editor: IncomeLineEditorState?,
    ) : MonthlyPlanUiState

    data class Error(
        val message: UiText,
    ) : MonthlyPlanUiState
}

/**
 * The income-line dialog: [editingId] is `null` for a new line. [labelError] is set when the last
 * save attempt was rejected, and the dialog stays open.
 */
data class IncomeLineEditorState(
    val editingId: String?,
    val initialLabel: String,
    val initialAmountCents: Long?,
    val labelError: UiText? = null,
)
