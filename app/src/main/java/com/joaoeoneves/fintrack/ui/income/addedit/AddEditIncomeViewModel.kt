package com.joaoeoneves.fintrack.ui.income.addedit

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText
import com.joaoeoneves.fintrack.ui.navigation.AddEditIncome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

private const val TAG = "AddEditIncomeViewModel"

@HiltViewModel
class AddEditIncomeViewModel
    @Inject
    constructor(
        private val incomeRepository: IncomeRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditIncome>()

        val isEditRoute: Boolean = route.incomeId != null

        private val _uiState = MutableStateFlow<AddEditIncomeUiState>(AddEditIncomeUiState.Loading)
        val uiState: StateFlow<AddEditIncomeUiState> = _uiState.asStateFlow()

        private val savedEvents = Channel<Unit>(Channel.BUFFERED)
        val savedEvent: Flow<Unit> = savedEvents.receiveAsFlow()

        private val deletedEvents = Channel<Income>(Channel.BUFFERED)

        /** Emits the deleted income (as loaded) so the screen can offer to undo the deletion. */
        val deletedEvent: Flow<Income> = deletedEvents.receiveAsFlow()

        // The income as loaded in edit mode, kept so a deletion can be undone.
        private var loaded: Income? = null

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
                            // Prefill with the locale's own decimal separator, matching how amounts
                            // are displayed (parseAmountCents accepts either '.' or ',').
                            val separator = decimalSeparatorFor(Locale.getDefault(Locale.Category.FORMAT))
                            loaded = existing
                            _uiState.value =
                                if (existing != null) {
                                    AddEditIncomeUiState.Ready(
                                        IncomeFormState(
                                            source = existing.source,
                                            amountText = existing.amountCents.toAmountInputText(separator),
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
                            Log.w(TAG, "Failed to load income", e)
                            _uiState.value =
                                AddEditIncomeUiState.Error(UiText.StringResource(R.string.error_generic_fallback))
                        }
                } else {
                    _uiState.value = AddEditIncomeUiState.Ready(IncomeFormState(date = Instant.now()))
                }
            }
        }

        fun onRetry() = loadIncome()

        fun onSourceChanged(source: String) {
            updateForm {
                it.copy(
                    source = limitInput(it.source, source, MAX_NAME_LENGTH),
                    sourceError = null,
                    saveError = null,
                )
            }
        }

        fun onAmountChanged(amountText: String) {
            updateForm { it.copy(amountText = amountText, amountError = null, saveError = null) }
        }

        fun onDateSelected(date: Instant) = updateForm { it.copy(date = date, saveError = null) }

        fun onNoteChanged(note: String) {
            updateForm { it.copy(note = limitInput(it.note, note, MAX_NOTE_LENGTH), saveError = null) }
        }

        fun onSave() {
            val current = _uiState.value
            if (current !is AddEditIncomeUiState.Ready || current.form.isSaving) return
            val form = current.form

            val sourceError = if (form.source.isBlank()) UiText.StringResource(R.string.error_source_required) else null
            val amountResult = parseAmountCents(form.amountText)
            val amountError = if (amountResult.isFailure) UiText.StringResource(R.string.error_invalid_amount) else null

            if (sourceError != null || amountError != null) {
                _uiState.value =
                    AddEditIncomeUiState.Ready(form.copy(sourceError = sourceError, amountError = amountError))
                return
            }

            val amountCents = amountResult.getOrThrow()
            _uiState.value = AddEditIncomeUiState.Ready(form.copy(isSaving = true))
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
                            Log.w(TAG, "Failed to save income", e)
                            val saveError = UiText.StringResource(R.string.error_failed_save_income)
                            _uiState.value =
                                AddEditIncomeUiState.Ready(latest.form.copy(isSaving = false, saveError = saveError))
                        }
                    }
            }
        }

        /** Deletes the income being edited; the screen then closes and offers an undo. */
        fun onDelete() {
            val current = _uiState.value
            val income = loaded
            if (current !is AddEditIncomeUiState.Ready || !current.form.isEditMode || income == null) return
            if (current.form.isSaving || current.form.isDeleting) return
            _uiState.value = AddEditIncomeUiState.Ready(current.form.copy(isDeleting = true, saveError = null))
            viewModelScope.launch {
                incomeRepository
                    .deleteIncome(income.id)
                    .onSuccess {
                        deletedEvents.send(income)
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to delete income", e)
                        updateForm {
                            it.copy(
                                isDeleting = false,
                                saveError = UiText.StringResource(R.string.error_failed_delete_income),
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
