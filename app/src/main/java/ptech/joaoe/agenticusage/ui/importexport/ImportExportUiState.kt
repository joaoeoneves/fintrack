package ptech.joaoe.agenticusage.ui.importexport

import ptech.joaoe.agenticusage.data.csv.CsvRowFailure
import ptech.joaoe.agenticusage.domain.model.Expense

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

    data class Error(
        val message: String,
    ) : ImportUiState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState

    data object Exporting : ExportUiState

    data class Done(
        val exportedCount: Int,
    ) : ExportUiState

    data class Error(
        val message: String,
    ) : ExportUiState
}
