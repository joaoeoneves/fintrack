package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.YearMonth

// Aliases kept so the dashboard composables can keep referring to the moved domain types unqualified.
typealias CategoryTotal = com.joaoeoneves.fintrack.domain.model.CategoryTotal
typealias CategoryBudget = com.joaoeoneves.fintrack.domain.model.CategoryBudget

/**
 * What was put into investments: [monthCents] in the viewed month (`null` when a whole year is shown) and [yearCents]
 * in [year] up to the viewed month. [ratePercent] is the share of what was received that went into investments
 * (the month's for a month, the year's for a year), `null` when it cannot be worked out.
 */
data class InvestedFigure(
    val monthCents: Long?,
    val yearCents: Long,
    val year: Int,
    val ratePercent: Int? = null,
) {
    /** The amount Home shows: the month's when a month is viewed, else the year's. */
    val shownCents: Long get() = monthCents ?: yearCents
}

/**
 * [investedCents] as a whole percent of [receivedCents], rounded half up; `null` when nothing was received or invested.
 * May exceed 100.
 */
fun investmentRatePercent(
    investedCents: Long,
    receivedCents: Long,
): Int? =
    if (receivedCents <= 0L || investedCents <= 0L) {
        null
    } else {
        ((investedCents * 2 * PERCENT + receivedCents) / (receivedCents * 2)).toInt()
    }

private const val PERCENT = 100L

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
     * @property spendingAverageMonths how many earlier months the average covers.
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
        val spendingAverageMonths: Int = 0,
    ) : DashboardUiState

    data class Error(
        val message: UiText,
    ) : DashboardUiState
}
