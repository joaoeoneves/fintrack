package com.joaoeoneves.fintrack.ui.income.addedit

import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import java.time.Instant

data class IncomeFormState(
    val source: String = "",
    val amountText: String = "",
    val date: Instant = Instant.now(),
    val note: String = "",
    val sourceError: UiText? = null,
    val amountError: UiText? = null,
    val saveError: UiText? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isEditMode: Boolean = false,
    val incomeId: String? = null,
) {
    val isValid: Boolean
        get() = source.isNotBlank() && parseAmountCents(amountText).isSuccess

    val isBusy: Boolean
        get() = isSaving || isDeleting

    val canDelete: Boolean
        get() = isEditMode && incomeId != null
}

sealed interface AddEditIncomeUiState {
    data object Loading : AddEditIncomeUiState

    data class Ready(
        val form: IncomeFormState,
    ) : AddEditIncomeUiState

    data class Error(
        val message: UiText,
    ) : AddEditIncomeUiState
}
