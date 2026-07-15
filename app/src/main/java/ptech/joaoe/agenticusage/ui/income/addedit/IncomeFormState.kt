package ptech.joaoe.agenticusage.ui.income.addedit

import ptech.joaoe.agenticusage.ui.expense.addedit.parseAmountCents
import java.time.Instant

data class IncomeFormState(
    val source: String = "",
    val amountText: String = "",
    val date: Instant = Instant.now(),
    val note: String = "",
    val sourceError: String? = null,
    val amountError: String? = null,
    val saveError: String? = null,
    val isEditMode: Boolean = false,
    val incomeId: String? = null,
) {
    val isValid: Boolean
        get() = source.isNotBlank() && parseAmountCents(amountText).isSuccess
}

sealed interface AddEditIncomeUiState {
    data object Loading : AddEditIncomeUiState

    data class Ready(
        val form: IncomeFormState,
    ) : AddEditIncomeUiState
}
