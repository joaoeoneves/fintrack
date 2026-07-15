package ptech.joaoe.agenticusage.ui.dashboard

import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.model.TimeRange

data class CategoryTotal(
    val category: ExpenseCategory,
    val totalCents: Long,
)

/**
 * Progress of a single [ExpenseCategory] against its budget limit for the current calendar
 * month. [limitCents] is `null` when no budget has been set for the category.
 */
data class CategoryBudget(
    val category: ExpenseCategory,
    val limitCents: Long?,
    val spentCents: Long,
) {
    val isOverBudget: Boolean get() = limitCents != null && spentCents > limitCents
}

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Content(
        val timeRange: TimeRange,
        val expenses: List<Expense>,
        val categoryTotals: List<CategoryTotal>,
        val totalCents: Long,
        val recentExpenses: List<Expense>,
        val budgets: List<CategoryBudget>,
    ) : DashboardUiState

    data class Error(
        val message: String,
    ) : DashboardUiState
}
