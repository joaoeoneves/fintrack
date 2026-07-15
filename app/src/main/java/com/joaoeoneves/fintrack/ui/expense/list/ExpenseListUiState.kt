package com.joaoeoneves.fintrack.ui.expense.list

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.TimeRange

sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState

    data class Content(
        val timeRange: TimeRange,
        val query: String,
        val sortOption: SortOption,
        val expenses: List<Expense>,
    ) : ExpenseListUiState

    data class Error(
        val message: String,
    ) : ExpenseListUiState
}
