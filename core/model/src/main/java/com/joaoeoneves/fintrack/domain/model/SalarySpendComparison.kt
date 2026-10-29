package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * An ad-hoc comparison for a salary line: what was received at the end of [receivedMonth] against what was spent in
 * the month after it ([spentMonth], still partial when [spentIsPartial]). No difference or verdict is derived.
 */
data class SalarySpendComparison(
    val receivedMonth: YearMonth,
    val receivedCents: Long,
    val spentMonth: YearMonth,
    val spentCents: Long,
    val spentIsPartial: Boolean,
)

/**
 * The comparison for the selected [month] (`null` on the year scale), or `null` when nothing was received in it or the
 * following month has not started yet ([today] is the current month). [nextMonthSpendingCents] is the SPENDING-kind
 * total of the following month (the Spending tab's figure, investments excluded).
 */
fun salarySpendComparisonOf(
    month: YearMonth?,
    receivedCents: Long,
    nextMonthSpendingCents: Long,
    today: YearMonth,
): SalarySpendComparison? {
    if (month == null || receivedCents <= 0L) return null
    val next = month.plusMonths(1)
    return if (next > today) {
        null
    } else {
        SalarySpendComparison(month, receivedCents, next, nextMonthSpendingCents, spentIsPartial = next == today)
    }
}
