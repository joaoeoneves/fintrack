package com.joaoeoneves.fintrack.ui.income.addedit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.navigation.AddEditIncome
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
class AddEditIncomeViewModel
    @Inject
    constructor(
        private val incomeRepository: IncomeRepository,
        savedStateHandle: SavedStateHandle,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallbacks below match the exact literals those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditIncome>()

        val isEditRoute: Boolean = route.incomeId != null

        private val _uiState = MutableStateFlow<AddEditIncomeUiState>(AddEditIncomeUiState.Loading)
        val uiState: StateFlow<AddEditIncomeUiState> = _uiState.asStateFlow()

        private val savedEvents = Channel<Unit>(Channel.BUFFERED)
        val savedEvent: Flow<Unit> = savedEvents.receiveAsFlow()

        init {
            loadIncome()
        }

        private fun loadIncome() {
            viewModelScope.launch {
                _uiState.value = AddEditIncomeUiState.Loading
                val incomeId = route.incomeId
                if (incomeId != null) {
                    incomeRepository
                        .getIncome(incomeId)
                        .onSuccess { existing ->
                            _uiState.value =
                                if (existing != null) {
                                    AddEditIncomeUiState.Ready(
                                        IncomeFormState(
                                            source = existing.source,
                                            amountText = existing.amountCents.toAmountText(),
                                            date = existing.date,
                                            note = existing.note ?: "",
                                            isEditMode = true,
                                            incomeId = existing.id,
                                        ),
                                    )
                                } else {
                                    AddEditIncomeUiState.Ready(
                                        IncomeFormState(isEditMode = true, incomeId = incomeId),
                                    )
                                }
                        }.onFailure { e ->
                            val fallback = context?.getString(R.string.error_generic_fallback) ?: "Something went wrong"
                            _uiState.value = AddEditIncomeUiState.Error(e.message ?: fallback)
                        }
                } else {
                    _uiState.value = AddEditIncomeUiState.Ready(IncomeFormState(date = Instant.now()))
                }
            }
        }

        fun onRetry() = loadIncome()

        fun onSourceChanged(source: String) = updateForm { it.copy(source = source, sourceError = null, saveError = null) }

        fun onAmountChanged(amountText: String) = updateForm { it.copy(amountText = amountText, amountError = null, saveError = null) }

        fun onDateSelected(date: Instant) = updateForm { it.copy(date = date, saveError = null) }

        fun onNoteChanged(note: String) = updateForm { it.copy(note = note, saveError = null) }

        fun onSave() {
            val current = _uiState.value
            if (current !is AddEditIncomeUiState.Ready) return
            val form = current.form

            val sourceRequiredMessage = context?.getString(R.string.error_source_required) ?: "Source is required"
            val invalidAmountMessage = context?.getString(R.string.error_invalid_amount) ?: "Enter a valid amount"
            val sourceError = if (form.source.isBlank()) sourceRequiredMessage else null
            val amountResult = parseAmountCents(form.amountText)
            val amountError = if (amountResult.isFailure) invalidAmountMessage else null

            if (sourceError != null || amountError != null) {
                _uiState.value = AddEditIncomeUiState.Ready(form.copy(sourceError = sourceError, amountError = amountError))
                return
            }

            val amountCents = amountResult.getOrThrow()
            viewModelScope.launch {
                val income =
                    Income(
                        id = form.incomeId.orEmpty(),
                        source = form.source.trim(),
                        amountCents = amountCents,
                        date = form.date,
                        note = form.note.trim().ifBlank { null },
                    )
                val result =
                    if (form.isEditMode) {
                        incomeRepository.updateIncome(income)
                    } else {
                        incomeRepository.addIncome(income)
                    }
                result
                    .onSuccess {
                        savedEvents.send(Unit)
                    }.onFailure { e ->
                        val latest = _uiState.value
                        if (latest is AddEditIncomeUiState.Ready) {
                            val failedSaveMessage =
                                context?.getString(R.string.error_failed_save_income) ?: "Failed to save income"
                            _uiState.value =
                                AddEditIncomeUiState.Ready(
                                    latest.form.copy(saveError = e.message ?: failedSaveMessage),
                                )
                        }
                    }
            }
        }

        private inline fun updateForm(transform: (IncomeFormState) -> IncomeFormState) {
            val current = _uiState.value
            if (current is AddEditIncomeUiState.Ready) {
                _uiState.value = AddEditIncomeUiState.Ready(transform(current.form))
            }
        }
    }

private fun Long.toAmountText(): String {
    val whole = this / 100
    val fraction = (this % 100).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}
