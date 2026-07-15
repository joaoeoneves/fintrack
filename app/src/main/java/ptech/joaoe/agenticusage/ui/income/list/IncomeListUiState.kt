package ptech.joaoe.agenticusage.ui.income.list

import ptech.joaoe.agenticusage.domain.model.Income
import ptech.joaoe.agenticusage.domain.model.TimeRange

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
