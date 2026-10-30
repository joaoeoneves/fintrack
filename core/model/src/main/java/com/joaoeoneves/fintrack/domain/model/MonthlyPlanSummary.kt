package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * The monthly plan evaluated for one month. Planned money counts as used even before it is spent;
 * overspending uses more; unbudgeted spending counts in full.
 *
 * @property expectedIncomeCents sum of the regular (monthly) planned income lines active in the month.
 * @property extrasExpectedCents sum of the yearly and one-off lines landing in the month; kept out of
 * [incomeBasisCents] and so out of safe-to-spend.
 * @property actualIncomeCents sum of the income dated in the month.
 * @property plannedSpendingCents sum of the planned (recurring) amounts of spending buckets.
 * @property plannedSavingCents sum of the planned amounts of saving buckets.
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
    val extrasExpectedCents: Long = 0L,
) {
    val plannedAllocationsCents: Long get() = plannedSpendingCents + plannedSavingCents
    val plannedLeftoverCents: Long get() = expectedIncomeCents - plannedAllocationsCents
    val incomeBasisCents: Long get() = maxOf(expectedIncomeCents, actualIncomeCents)

    /** Income basis minus everything committed; negative when the plan is overrun. */
    val safeToSpendCents: Long get() = incomeBasisCents - committedCents
}

/**
 * Evaluates the plan for [month]: [buckets] are the month's budget rows, [monthIncome] only that month's
 * income and [workingDays] the working days of the month (per-working-day lines multiply by it). With
 * [envelopes] the buckets filed under one commit pooled (the larger of its amount and its spend).
 */
@Suppress("LongParameterList") // The plan's inputs, one per source.
fun monthlyPlanOf(
    buckets: List<CategoryBudget>,
    plannedIncome: List<PlannedIncome>,
    monthIncome: List<Income>,
    month: YearMonth,
    workingDays: Int,
    envelopes: List<Envelope> = emptyList(),
): MonthlyPlanSummary {
    val income = IncomeMonth.forMonth(month, workingDays)
    val activeIncome = plannedIncome.asOf(month)
    val (extras, regular) = activeIncome.partition { it.isExtra }
    val split = splitBuckets(buckets, envelopes)
    val perCategory = split.byEnvelope.flatMap { it.second } + split.loose
    val limited = perCategory.filter { it.planned != null }
    val spendingLimited = limited.filter { it.category.kind == CategoryKind.SPENDING }
    val pooledCommitted =
        split.byEnvelope.sumOf { (_, rows) -> maxOf(monthAmountOf(rows) ?: 0L, rows.sumOf { it.spentCents }) }
    val pooledRemaining =
        split.byEnvelope.sumOf { (_, rows) ->
            monthAmountOf(rows)?.let { maxOf(it - rows.sumOf { row -> row.spentCents }, 0L) } ?: 0L
        }
    return MonthlyPlanSummary(
        expectedIncomeCents = regular.sumOf { income.amountIn(it, month) },
        extrasExpectedCents = extras.sumOf { income.amountIn(it, month) },
        actualIncomeCents = monthIncome.sumOf { it.amountCents },
        plannedSpendingCents = spendingLimited.sumOf { it.planned ?: 0L },
        plannedSavingCents = limited.filter { it.category.kind == CategoryKind.SAVING }.sumOf { it.planned ?: 0L },
        committedCents = pooledCommitted + split.loose.sumOf { maxOf(it.planned ?: 0L, it.spentCents) },
        remainingInBucketsCents =
            pooledRemaining +
                split.loose
                    .filter { it.planned != null && it.category.kind == CategoryKind.SPENDING }
                    .sumOf { maxOf((it.planned ?: 0L) - it.spentCents, 0L) },
        isConfigured = activeIncome.isNotEmpty(),
    )
}

/** Buckets with a positive limit as chart slices of their planned amount, in the given (catalog) order. */
fun List<CategoryBudget>.plannedSlices(): List<CategoryTotal> =
    mapNotNull { bucket ->
        bucket.planned?.takeIf { it > 0 }?.let { CategoryTotal(bucket.category, it) }
    }
