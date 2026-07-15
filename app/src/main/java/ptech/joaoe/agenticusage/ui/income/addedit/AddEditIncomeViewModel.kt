package ptech.joaoe.agenticusage.ui.income.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ptech.joaoe.agenticusage.domain.model.Income
import ptech.joaoe.agenticusage.domain.repository.IncomeRepository
import ptech.joaoe.agenticusage.ui.expense.addedit.parseAmountCents
import ptech.joaoe.agenticusage.ui.navigation.AddEditIncome
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AddEditIncomeViewModel
    @Inject
    constructor(
        private val incomeRepository: IncomeRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditIncome>()

        private val _uiState = MutableStateFlow<AddEditIncomeUiState>(AddEditIncomeUiState.Loading)
        val uiState: StateFlow<AddEditIncomeUiState> = _uiState.asStateFlow()

        private val savedEvents = Channel<Unit>(Channel.BUFFERED)
        val savedEvent: Flow<Unit> = savedEvents.receiveAsFlow()

        init {
            viewModelScope.launch {
                val incomeId = route.incomeId
                if (incomeId != null) {
                    val existing = incomeRepository.getIncome(incomeId)
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
                } else {
                    _uiState.value = AddEditIncomeUiState.Ready(IncomeFormState(date = Instant.now()))
                }
            }
        }

        fun onSourceChanged(source: String) = updateForm { it.copy(source = source, sourceError = null, saveError = null) }

        fun onAmountChanged(amountText: String) = updateForm { it.copy(amountText = amountText, amountError = null, saveError = null) }

        fun onDateSelected(date: Instant) = updateForm { it.copy(date = date, saveError = null) }

        fun onNoteChanged(note: String) = updateForm { it.copy(note = note, saveError = null) }

        fun onSave() {
            val current = _uiState.value
            if (current !is AddEditIncomeUiState.Ready) return
            val form = current.form

            val sourceError = if (form.source.isBlank()) "Source is required" else null
            val amountResult = parseAmountCents(form.amountText)
            val amountError = if (amountResult.isFailure) "Enter a valid amount" else null

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
                            _uiState.value =
                                AddEditIncomeUiState.Ready(
                                    latest.form.copy(saveError = e.message ?: "Failed to save income"),
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
