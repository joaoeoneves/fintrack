package ptech.joaoe.agenticusage.ui.expense.addedit

import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import java.time.Instant

data class ExpenseFormState(
    val name: String = "",
    val amountText: String = "",
    val category: ExpenseCategory = ExpenseCategory.SHOPPING,
    val date: Instant = Instant.now(),
    val note: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val saveError: String? = null,
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
}
