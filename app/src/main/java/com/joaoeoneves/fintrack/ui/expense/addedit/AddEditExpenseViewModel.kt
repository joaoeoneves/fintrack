package com.joaoeoneves.fintrack.ui.expense.addedit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.ui.navigation.AddEditExpense
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AddEditExpenseViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        savedStateHandle: SavedStateHandle,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallbacks below match the exact literals those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditExpense>()

        private val _uiState = MutableStateFlow<AddEditUiState>(AddEditUiState.Loading)
        val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

        private val savedEvents = Channel<Unit>(Channel.BUFFERED)
        val savedEvent: Flow<Unit> = savedEvents.receiveAsFlow()

        init {
            viewModelScope.launch {
                val expenseId = route.expenseId
                if (expenseId != null) {
                    val existing = expenseRepository.getExpense(expenseId)
                    _uiState.value =
                        if (existing != null) {
                            AddEditUiState.Ready(
                                ExpenseFormState(
                                    name = existing.name,
                                    amountText = existing.amountCents.toAmountText(),
                                    category = existing.category,
                                    date = existing.date,
                                    note = existing.note ?: "",
                                    isEditMode = true,
                                    expenseId = existing.id,
                                ),
                            )
                        } else {
                            AddEditUiState.Ready(
                                ExpenseFormState(isEditMode = true, expenseId = expenseId),
                            )
                        }
                } else {
                    _uiState.value = AddEditUiState.Ready(ExpenseFormState(date = Instant.now()))
                }
            }
        }

        fun onNameChanged(name: String) = updateForm { it.copy(name = name, nameError = null, saveError = null) }

        fun onAmountChanged(amountText: String) = updateForm { it.copy(amountText = amountText, amountError = null, saveError = null) }

        fun onCategorySelected(category: ExpenseCategory) = updateForm { it.copy(category = category, saveError = null) }

        fun onDateSelected(date: Instant) = updateForm { it.copy(date = date, saveError = null) }

        fun onNoteChanged(note: String) = updateForm { it.copy(note = note, saveError = null) }

        fun onSave() {
            val current = _uiState.value
            if (current !is AddEditUiState.Ready) return
            val form = current.form

            val nameRequiredMessage = context?.getString(R.string.error_name_required) ?: "Name is required"
            val invalidAmountMessage = context?.getString(R.string.error_invalid_amount) ?: "Enter a valid amount"
            val nameError = if (form.name.isBlank()) nameRequiredMessage else null
            val amountResult = parseAmountCents(form.amountText)
            val amountError = if (amountResult.isFailure) invalidAmountMessage else null

            if (nameError != null || amountError != null) {
                _uiState.value = AddEditUiState.Ready(form.copy(nameError = nameError, amountError = amountError))
                return
            }

            val amountCents = amountResult.getOrThrow()
            viewModelScope.launch {
                val expense =
                    Expense(
                        id = form.expenseId.orEmpty(),
                        name = form.name.trim(),
                        amountCents = amountCents,
                        category = form.category,
                        date = form.date,
                        note = form.note.trim().ifBlank { null },
                    )
                val result =
                    if (form.isEditMode) {
                        expenseRepository.updateExpense(expense)
                    } else {
                        expenseRepository.addExpense(expense)
                    }
                result
                    .onSuccess {
                        savedEvents.send(Unit)
                    }.onFailure { e ->
                        val latest = _uiState.value
                        if (latest is AddEditUiState.Ready) {
                            val failedSaveMessage =
                                context?.getString(R.string.error_failed_save_expense) ?: "Failed to save expense"
                            _uiState.value =
                                AddEditUiState.Ready(
                                    latest.form.copy(saveError = e.message ?: failedSaveMessage),
                                )
                        }
                    }
            }
        }

        private inline fun updateForm(transform: (ExpenseFormState) -> ExpenseFormState) {
            val current = _uiState.value
            if (current is AddEditUiState.Ready) {
                _uiState.value = AddEditUiState.Ready(transform(current.form))
            }
        }
    }

private fun Long.toAmountText(): String {
    val whole = this / 100
    val fraction = (this % 100).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}
