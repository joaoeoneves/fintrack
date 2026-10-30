package com.joaoeoneves.fintrack.ui.importexport

import com.joaoeoneves.fintrack.ui.common.UiText

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
