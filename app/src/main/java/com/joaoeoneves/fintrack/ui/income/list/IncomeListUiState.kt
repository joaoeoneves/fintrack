package com.joaoeoneves.fintrack.ui.income.list

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.expense.list.SortOption

sealed interface IncomeListUiState {
    data object Loading : IncomeListUiState

    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val query: String,
        val sortOption: SortOption,
        val income: List<Income>,
    ) : IncomeListUiState

    data class Error(
        val message: UiText,
    ) : IncomeListUiState
}
