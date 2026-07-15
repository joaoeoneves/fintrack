package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange

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
        val incomeCents: Long,
        val netCents: Long,
        val recentIncome: List<Income>,
    ) : DashboardUiState

    data class Error(
        val message: String,
    ) : DashboardUiState
}
