package com.joaoeoneves.fintrack.ui.income.list

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange

sealed interface IncomeListUiState {
    data object Loading : IncomeListUiState

    data class Content(
        val timeRange: TimeRange,
        val income: List<Income>,
    ) : IncomeListUiState

    data class Error(
        val message: String,
    ) : IncomeListUiState
}
