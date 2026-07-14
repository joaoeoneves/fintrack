package ptech.joaoe.agenticusage.ui.expense.list

import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.TimeRange

sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState

    data class Content(
        val timeRange: TimeRange,
        val query: String,
        val sortOption: SortOption,
        val expenses: List<Expense>
    ) : ExpenseListUiState

    data class Error(val message: String) : ExpenseListUiState
}
