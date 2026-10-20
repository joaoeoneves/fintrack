package com.joaoeoneves.fintrack.ui.income.list

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period

sealed interface IncomeListUiState {
    data object Loading : IncomeListUiState

    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val query: String,
        val sortOption: IncomeSortOption,
        val income: List<Income>,
    ) : IncomeListUiState

    data class Error(
        val message: String,
    ) : IncomeListUiState
}
