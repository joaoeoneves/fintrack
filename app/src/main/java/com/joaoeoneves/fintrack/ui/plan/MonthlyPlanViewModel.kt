package com.joaoeoneves.fintrack.ui.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.monthlyPlanOf
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MonthlyPlanViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Edits the global monthly plan: expected-income lines and the per-category planned allocations. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyPlanViewModel
    @Inject
    constructor(
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val budgetRepository: BudgetRepository,
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val editor = MutableStateFlow<IncomeLineEditorState?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)

        /** One-shot user-facing messages for plan writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<MonthlyPlanUiState> =
            retryTrigger
                .flatMapLatest { planFlow(plannedIncomeRepository, budgetRepository, categoryRepository, editor) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = MonthlyPlanUiState.Loading,
                )

        fun onRetry() {
            retryTrigger.value++
        }

        fun onAddIncomeLine() {
            viewModelScope.launch {
                if (currentLines().size < MAX_PLANNED_INCOME_LINES) {
                    editor.value = IncomeLineEditorState(editingId = null, initialLabel = "", initialAmountCents = null)
                }
            }
        }

        fun onEditIncomeLine(id: String) {
            viewModelScope.launch {
                currentLines().firstOrNull { it.id == id }?.let { line ->
                    editor.value =
                        IncomeLineEditorState(
                            editingId = line.id,
                            initialLabel = line.label,
                            initialAmountCents = line.amountCents,
                        )
                }
            }
        }

        fun onDismissEditor() {
            editor.value = null
        }

        /** Saves the open editor's line with the trimmed [label]; a rejected label keeps the editor open. */
        fun onSaveIncomeLine(
            label: String,
            amountCents: Long,
        ) {
            val current = editor.value ?: return
            val trimmed = label.trim()
            val error = validateIncomeLineLabel(trimmed)
            if (error != null) {
                editor.value = current.copy(labelError = error)
                return
            }
            viewModelScope.launch {
                val lines = currentLines()
                val existing = lines.firstOrNull { it.id == current.editingId }
                val line =
                    PlannedIncome(
                        id = existing?.id.orEmpty(),
                        label = trimmed,
                        amountCents = amountCents,
                        order = existing?.order ?: ((lines.maxOfOrNull { it.order } ?: -1) + 1),
                    )
                plannedIncomeRepository
                    .savePlannedIncome(line)
                    .onSuccess { editor.value = null }
                    .onFailure { reportFailure(it) }
            }
        }

        fun onDeleteIncomeLine(id: String) {
            viewModelScope.launch {
                plannedIncomeRepository
                    .deletePlannedIncome(id)
                    .onSuccess { editor.value = null }
                    .onFailure { reportFailure(it) }
            }
        }

        fun onSetAllocation(
            category: Category,
            limitCents: Long,
        ) {
            viewModelScope.launch {
                budgetRepository.setBudget(category.id, limitCents).onFailure { reportFailure(it) }
            }
        }

        fun onClearAllocation(category: Category) {
            viewModelScope.launch {
                budgetRepository.clearBudget(category.id).onFailure { reportFailure(it) }
            }
        }

        private suspend fun currentLines(): List<PlannedIncome> = plannedIncomeRepository.observePlannedIncome().first()

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to save the monthly plan", error)
            errors.send(UiText.StringResource(R.string.error_plan_save_failed))
        }
    }

private fun planFlow(
    plannedIncomeRepository: PlannedIncomeRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    editor: Flow<IncomeLineEditorState?>,
): Flow<MonthlyPlanUiState> =
    combine(
        plannedIncomeRepository.observePlannedIncome(),
        budgetRepository.observeBudgets(),
        categoryRepository.observeCategories(),
        editor,
    ) { lines, budgets, categories, editorState ->
        val allocations = budgetProgressOf(emptyList(), budgets, categories)
        val content: MonthlyPlanUiState =
            MonthlyPlanUiState.Content(
                incomeLines = lines,
                allocations = allocations,
                summary = monthlyPlanOf(allocations, lines, emptyList()),
                canAddIncomeLine = lines.size < MAX_PLANNED_INCOME_LINES,
                editor = editorState,
            )
        content
    }.catch { e ->
        Log.w(TAG, "Failed to observe the monthly plan", e)
        emit(MonthlyPlanUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
    }
