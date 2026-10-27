package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.DailyAllowance
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PotsSummary
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.YearMonth

// Aliases kept so the dashboard composables can keep referring to the moved domain types unqualified.
typealias CategoryTotal = com.joaoeoneves.fintrack.domain.model.CategoryTotal
typealias CategoryBudget = com.joaoeoneves.fintrack.domain.model.CategoryBudget

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /**
     * @property budgetMonth the calendar month the plan is evaluated against: the selected month.
     * @property income every income entry in the period; with [expenses] and [range] it feeds the balance chart.
     * @property range the instant range [period] covers.
     * @property outflowCents everything that left in the period, i.e. [spendingCents] + [savedCents].
     * @property netCents income minus all outflow (spending and saving alike).
     * @property plan the monthly plan evaluated for [budgetMonth].
     * @property recent the latest expenses and income merged, newest first, at most [RECENT_ITEMS_LIMIT].
     * @property dailyAllowance what can be spent per remaining day; only for the current month.
     * @property fixedSpendingCents the part of [spendingCents] that came from recurring items; the rest is variable.
     * @property pots the plan split into earmarked pots and the base; `null` falls back to the plain plan.
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
        val outflowCents: Long,
        val spendingCents: Long,
        val savedCents: Long,
        val savingsRate: Double?,
        val recent: List<Transaction>,
        val incomeCents: Long,
        val netCents: Long,
        val plan: MonthlyPlanSummary,
        val dailyAllowance: DailyAllowance?,
        val fixedSpendingCents: Long = 0L,
        val pots: PotsSummary? = null,
    ) : DashboardUiState {
        /** What is free to spend: the base's free money (the plan's safe-to-spend when there are no earmarks). */
        val safeToSpendCents: Long get() = pots?.base?.freeCents ?: plan.safeToSpendCents
    }

    data class Error(
        val message: UiText,
    ) : DashboardUiState
}

/** How many entries Home's merged "recent activity" preview shows. */
const val RECENT_ITEMS_LIMIT = 5

/** The [limit] newest of [expenses] and [income] together, newest first. */
fun mergeRecent(
    expenses: List<Expense>,
    income: List<Income>,
    limit: Int = RECENT_ITEMS_LIMIT,
): List<Transaction> = (expenses + income).sortedByDescending { it.date }.take(limit)
