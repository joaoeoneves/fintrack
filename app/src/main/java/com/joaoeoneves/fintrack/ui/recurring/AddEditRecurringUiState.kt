package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface AddEditRecurringUiState {
    data object Loading : AddEditRecurringUiState

    data class Ready(
        val form: RecurringForm,
        val errors: RecurringFormErrors? = null,
        val saveError: UiText? = null,
        val isSaving: Boolean = false,
        val isEditMode: Boolean = false,
    ) : AddEditRecurringUiState

    data class Error(
        val message: UiText,
    ) : AddEditRecurringUiState
}
