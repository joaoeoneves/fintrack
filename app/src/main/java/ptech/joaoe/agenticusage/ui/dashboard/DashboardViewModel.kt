package ptech.joaoe.agenticusage.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.model.TimeRange
import ptech.joaoe.agenticusage.domain.model.currentCalendarMonthRange
import ptech.joaoe.agenticusage.domain.repository.BudgetRepository
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val budgetRepository: BudgetRepository,
    ) : ViewModel() {
        private val timeRange = MutableStateFlow(TimeRange.ONE_MONTH)
        private val retryTrigger = MutableStateFlow(0)

        private val monthRange = currentCalendarMonthRange()

        private fun budgetProgress(): Flow<List<CategoryBudget>> =
            combine(
                expenseRepository.observeExpenses(monthRange.startInclusive, monthRange.endExclusive),
                budgetRepository.observeBudgets(),
            ) { monthExpenses, budgets ->
                ExpenseCategory.entries.map { category ->
                    CategoryBudget(
                        category = category,
                        limitCents = budgets.find { it.category == category }?.limitCents,
                        spentCents = monthExpenses.filter { it.category == category }.sumOf { it.amountCents },
                    )
                }
            }

        val uiState: StateFlow<DashboardUiState> =
            combine(timeRange, retryTrigger) { range, _ -> range }
                .flatMapLatest { range ->
                    val instantRange = range.toInstantRange(now = Instant.now())
                    combine(
                        expenseRepository.observeExpenses(instantRange.startInclusive, instantRange.endExclusive),
                        budgetProgress(),
                    ) { expenses, budgets -> expenses.toContent(range, budgets) }
                        .map<DashboardUiState.Content, DashboardUiState> { content -> content }
                        .catch { e -> emit(DashboardUiState.Error(e.message ?: "Something went wrong")) }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DashboardUiState.Loading,
                )

        fun onTimeRangeSelected(range: TimeRange) {
            timeRange.value = range
        }

        fun onRetry() {
            retryTrigger.value++
        }

        fun onSetBudget(
            category: ExpenseCategory,
            limitCents: Long,
        ) {
            viewModelScope.launch { budgetRepository.setBudget(category, limitCents) }
        }

        private fun List<Expense>.toContent(
            range: TimeRange,
            budgets: List<CategoryBudget>,
        ): DashboardUiState.Content {
            val categoryTotals =
                ExpenseCategory.entries.map { category ->
                    CategoryTotal(
                        category = category,
                        totalCents = filter { it.category == category }.sumOf { it.amountCents },
                    )
                }
            return DashboardUiState.Content(
                timeRange = range,
                expenses = this,
                categoryTotals = categoryTotals,
                totalCents = sumOf { it.amountCents },
                recentExpenses = sortedByDescending { it.date }.take(5),
                budgets = budgets,
            )
        }
    }
