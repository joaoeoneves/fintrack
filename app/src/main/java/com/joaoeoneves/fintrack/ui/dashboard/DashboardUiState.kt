package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.YearMonth

data class CategoryTotal(
    val category: ExpenseCategory,
    val totalCents: Long,
)

/**
 * Progress of a single [ExpenseCategory] against its budget limit for the dashboard's budget
 * month (see [DashboardUiState.Content.budgetMonth]). [limitCents] is `null` when no budget has
 * been set for the category.
 */
data class CategoryBudget(
    val category: ExpenseCategory,
    val limitCents: Long?,
    val spentCents: Long,
) {
    val isOverBudget: Boolean get() = limitCents != null && spentCents > limitCents
}

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /**
     * @property budgetMonth the calendar month budgets are evaluated against: the selected month
     *   for month periods, otherwise the current month.
     * @property outflowCents everything that left in the period, i.e. [spendingCents] + [savedCents].
     * @property netCents income minus all outflow (spending and saving alike).
     * @property savingsRate [savedCents] as a fraction of [incomeCents], `null` without income.
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val budgetMonth: YearMonth,
        val budgetMonthIsCurrent: Boolean,
        val expenses: List<Expense>,
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
    ) : DashboardUiState

    data class Error(
        val message: UiText,
    ) : DashboardUiState
}
