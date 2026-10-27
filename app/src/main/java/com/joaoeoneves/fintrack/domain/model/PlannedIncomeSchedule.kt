package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** Whether this line pays out in [month]. */
fun PlannedIncome.isActiveIn(month: YearMonth): Boolean =
    when (frequency) {
        IncomeFrequency.MONTHLY -> month.monthValue !in skipMonths
        IncomeFrequency.YEARLY -> monthOfYear == month.monthValue
        IncomeFrequency.ONCE -> yearMonth == month
    }

/** What this line pays in [month]: the rate times [workingDays] for per-working-day lines; 0 when inactive. */
fun PlannedIncome.amountIn(
    month: YearMonth,
    workingDays: Int,
): Long =
    when {
        !isActiveIn(month) -> 0L
        basis == IncomeBasis.PER_WORKING_DAY -> amountCents * workingDays
        else -> amountCents
    }

/** Yearly and one-off lines are "extras": shown apart and kept out of the safe-to-spend basis. */
val PlannedIncome.isExtra: Boolean get() = frequency != IncomeFrequency.MONTHLY
