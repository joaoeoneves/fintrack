package com.joaoeoneves.fintrack.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.currentCalendarMonthRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val budgetRepository: BudgetRepository,
        private val incomeRepository: IncomeRepository,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallback below matches the exact literal those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
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
                        incomeRepository.observeIncome(instantRange.startInclusive, instantRange.endExclusive),
                    ) { expenses, budgets, income -> expenses.toContent(range, budgets, income) }
                        .map<DashboardUiState.Content, DashboardUiState> { content -> content }
                        .catch { e ->
                            val fallback =
                                context?.getString(R.string.error_generic_fallback) ?: "Something went wrong"
                            emit(DashboardUiState.Error(e.message ?: fallback))
                        }
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
            income: List<Income>,
        ): DashboardUiState.Content {
            val categoryTotals =
                ExpenseCategory.entries.map { category ->
                    CategoryTotal(
                        category = category,
                        totalCents = filter { it.category == category }.sumOf { it.amountCents },
                    )
                }
            val totalCents = sumOf { it.amountCents }
            val incomeCents = income.sumOf { it.amountCents }
            return DashboardUiState.Content(
                timeRange = range,
                expenses = this,
                categoryTotals = categoryTotals,
                totalCents = totalCents,
                recentExpenses = sortedByDescending { it.date }.take(5),
                budgets = budgets,
                incomeCents = incomeCents,
                netCents = incomeCents - totalCents,
                recentIncome = income.sortedByDescending { it.date }.take(5),
            )
        }
    }
