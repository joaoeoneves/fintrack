package ptech.joaoe.agenticusage.ui.dashboard

import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.model.TimeRange

data class CategoryTotal(val category: ExpenseCategory, val totalCents: Long)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Content(
        val timeRange: TimeRange,
        val expenses: List<Expense>,
        val categoryTotals: List<CategoryTotal>,
        val totalCents: Long,
        val recentExpenses: List<Expense>
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}
