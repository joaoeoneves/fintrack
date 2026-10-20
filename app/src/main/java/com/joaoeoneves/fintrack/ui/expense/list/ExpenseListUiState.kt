package com.joaoeoneves.fintrack.ui.expense.list

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState

    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val query: String,
        val sortOption: SortOption,
        val expenses: List<Expense>,
        val categoryFilter: ExpenseCategory? = null,
    ) : ExpenseListUiState

    data class Error(
        val message: UiText,
    ) : ExpenseListUiState
}
