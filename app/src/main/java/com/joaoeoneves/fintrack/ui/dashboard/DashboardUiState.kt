package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.DailyAllowance
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.YearMonth

// Aliases kept so the dashboard composables can keep referring to the moved domain types unqualified.
typealias CategoryTotal = com.joaoeoneves.fintrack.domain.model.CategoryTotal
typealias CategoryBudget = com.joaoeoneves.fintrack.domain.model.CategoryBudget

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /**
     * @property budgetMonth the calendar month budgets are evaluated against: the selected month
     *   for month periods, otherwise the current month.
     * @property income every income entry in the period; with [expenses] and [range] it feeds the balance chart.
     * @property range the instant range [period] covers.
     * @property outflowCents everything that left in the period, i.e. [spendingCents] + [savedCents].
     * @property netCents income minus all outflow (spending and saving alike).
     * @property plan the monthly plan evaluated for [budgetMonth].
     * @property plannedSlices the planned allocation per category, for the "Planned" donut.
     * @property dailyAllowance what can be spent per remaining day; only for the current month.
     * @property savingsRate [savedCents] as a fraction of [incomeCents], `null` without income.
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val budgetMonth: YearMonth,
        val budgetMonthIsCurrent: Boolean,
        val expenses: List<Expense>,
        val income: List<Income>,
        val range: InstantRange,
        val categoryTotals: List<CategoryTotal>,
        val outflowCents: Long,
        val spendingCents: Long,
        val savedCents: Long,
        val savingsRate: Double?,
        val recentExpenses: List<Expense>,
        val budgets: List<CategoryBudget>,
        val incomeCents: Long,
        val netCents: Long,
        val recentIncome: List<Income>,
        val plan: MonthlyPlanSummary,
        val plannedSlices: List<CategoryTotal>,
        val dailyAllowance: DailyAllowance?,
    ) : DashboardUiState

    data class Error(
        val message: UiText,
    ) : DashboardUiState
}
