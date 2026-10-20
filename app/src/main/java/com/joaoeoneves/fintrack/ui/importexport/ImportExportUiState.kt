package com.joaoeoneves.fintrack.ui.importexport

import com.joaoeoneves.fintrack.data.csv.CsvRowFailure
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface ImportUiState {
    data object Idle : ImportUiState

    data object Loading : ImportUiState

    data class Preview(
        val validExpenses: List<Expense>,
        val failures: List<CsvRowFailure>,
    ) : ImportUiState

    data object Importing : ImportUiState

    data class Done(
        val importedCount: Int,
    ) : ImportUiState

    data class PartialFailure(
        val succeededCount: Int,
        val message: UiText,
    ) : ImportUiState

    data class Error(
        val message: UiText,
    ) : ImportUiState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState

    data object Exporting : ExportUiState

    data class Done(
        val exportedCount: Int,
    ) : ExportUiState

    data class Error(
        val message: UiText,
    ) : ExportUiState
}
