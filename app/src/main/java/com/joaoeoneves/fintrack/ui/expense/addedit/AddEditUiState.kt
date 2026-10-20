package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.Instant

data class ExpenseFormState(
    val name: String = "",
    val amountText: String = "",
    val category: ExpenseCategory = ExpenseCategory.SHOPPING,
    val date: Instant = Instant.now(),
    val note: String = "",
    val isRefund: Boolean = false,
    val nameError: UiText? = null,
    val amountError: UiText? = null,
    val saveError: UiText? = null,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val expenseId: String? = null,
) {
    val isValid: Boolean
        get() = name.isNotBlank() && parseAmountCents(amountText).isSuccess
}

sealed interface AddEditUiState {
    data object Loading : AddEditUiState

    data class Ready(
        val form: ExpenseFormState,
    ) : AddEditUiState

    data class Error(
        val message: UiText,
    ) : AddEditUiState
}
