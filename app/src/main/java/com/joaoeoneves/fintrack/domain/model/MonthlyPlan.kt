package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The monthly plan evaluated for one month. Planned money counts as used even before it is spent;
 * overspending uses more; unbudgeted spending counts in full.
 *
 * @property expectedIncomeCents sum of the planned income lines.
 * @property actualIncomeCents sum of the income dated in the month.
 * @property plannedSpendingCents sum of the limits of spending buckets.
 * @property plannedSavingCents sum of the limits of saving buckets.
 * @property committedCents over all buckets, the larger of the limit (or 0) and the spend.
 * @property remainingInBucketsCents over spending buckets with a limit, what is left of each limit.
 * @property isConfigured whether any planned income line exists.
 */
data class MonthlyPlanSummary(
    val expectedIncomeCents: Long,
    val actualIncomeCents: Long,
    val plannedSpendingCents: Long,
    val plannedSavingCents: Long,
    val committedCents: Long,
    val remainingInBucketsCents: Long,
    val isConfigured: Boolean,
) {
    val plannedAllocationsCents: Long get() = plannedSpendingCents + plannedSavingCents
    val plannedLeftoverCents: Long get() = expectedIncomeCents - plannedAllocationsCents
    val incomeBasisCents: Long get() = maxOf(expectedIncomeCents, actualIncomeCents)

    /** Income basis minus everything committed; negative when the plan is overrun. */
    val safeToSpendCents: Long get() = incomeBasisCents - committedCents
}

/** Evaluates the plan: [buckets] are the month's budget rows, [monthIncome] only that month's income. */
fun monthlyPlanOf(
    buckets: List<CategoryBudget>,
    plannedIncome: List<PlannedIncome>,
    monthIncome: List<Income>,
): MonthlyPlanSummary {
    val limited = buckets.filter { it.limitCents != null }
    val spendingLimited = limited.filter { it.category.kind == CategoryKind.SPENDING }
    return MonthlyPlanSummary(
        expectedIncomeCents = plannedIncome.sumOf { it.amountCents },
        actualIncomeCents = monthIncome.sumOf { it.amountCents },
        plannedSpendingCents = spendingLimited.sumOf { it.limitCents ?: 0L },
        plannedSavingCents = limited.filter { it.category.kind == CategoryKind.SAVING }.sumOf { it.limitCents ?: 0L },
        committedCents = buckets.sumOf { maxOf(it.limitCents ?: 0L, it.spentCents) },
        remainingInBucketsCents = spendingLimited.sumOf { maxOf((it.limitCents ?: 0L) - it.spentCents, 0L) },
        isConfigured = plannedIncome.isNotEmpty(),
    )
}

/** Buckets with a positive limit as chart slices of their planned amount, in the given (catalog) order. */
fun List<CategoryBudget>.plannedSlices(): List<CategoryTotal> =
    mapNotNull { bucket ->
        bucket.limitCents?.takeIf { it > 0 }?.let { CategoryTotal(bucket.category, it) }
    }

/** What can be spent per remaining day: [cents] over [daysLeft] days, today included. */
data class DailyAllowance(
    val cents: Long,
    val daysLeft: Int,
)

/** The daily allowance for the current [month] only, and only while there is something left to spend. */
fun dailyAllowanceOf(
    safeToSpendCents: Long,
    month: YearMonth,
    today: LocalDate,
): DailyAllowance? {
    if (safeToSpendCents <= 0 || YearMonth.from(today) != month) return null
    val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
    return DailyAllowance(cents = safeToSpendCents / daysLeft, daysLeft = daysLeft)
}
