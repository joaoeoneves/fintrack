package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.YearMonth

// Aliases kept so the dashboard composables can keep referring to the moved domain types unqualified.
typealias CategoryTotal = com.joaoeoneves.fintrack.domain.model.CategoryTotal
typealias CategoryBudget = com.joaoeoneves.fintrack.domain.model.CategoryBudget

/**
 * What was put into investments: [monthCents] in the viewed month (`null` when a whole year is shown) and [yearCents]
 * in [year] up to the viewed month.
 */
data class InvestedFigure(
    val monthCents: Long?,
    val yearCents: Long,
    val year: Int,
)

/** The average left over per closed month of the year up to [upTo]. */
data class LeftoverFigure(
    val averageCents: Long,
    val upTo: YearMonth,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /**
     * Home shows income and spending separately.
     *
     * @property budgetMonth the month shown (the last month of the year for a year period).
     * @property incomeCents income dated in the period, by its date.
     * @property expectedIncomeCents what the income lines expect in the period, `null` without lines.
     * @property spendingCents net outflow of SPENDING categories (investments are left out).
     * @property spendingVsAveragePercent signed change against the average of the earlier months, `null` for a year
     *   or without earlier data.
     * @property invested the "Invested" row, `null` when nothing was ever invested.
     * @property leftover the "Left over until" row, `null` without closed months.
     * @property spendingAverageMonths how many earlier months the average covers.
     * @property recent the latest expenses and income merged, newest first, at most [RECENT_ITEMS_LIMIT].
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val budgetMonth: YearMonth,
        val budgetMonthIsCurrent: Boolean,
        val incomeCents: Long,
        val expectedIncomeCents: Long?,
        val spendingCents: Long,
        val spendingVsAveragePercent: Int?,
        val invested: InvestedFigure?,
        val leftover: LeftoverFigure?,
        val recent: List<Transaction>,
        val spendingAverageMonths: Int = 0,
    ) : DashboardUiState

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
