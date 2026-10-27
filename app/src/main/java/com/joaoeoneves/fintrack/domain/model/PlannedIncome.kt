package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** How a planned income line's amount is computed. */
enum class IncomeBasis {
    /** [PlannedIncome.amountCents] is the amount paid in each active month. */
    FIXED,

    /** [PlannedIncome.amountCents] is the rate per working day; the month's total is rate x days. */
    PER_WORKING_DAY,
}

/** How often a planned income line is paid. MONTHLY lines are "regular"; YEARLY and ONCE are "extras". */
enum class IncomeFrequency { MONTHLY, YEARLY, ONCE }

/**
 * One line of expected income in the plan (e.g. salary, meal allowance, holiday bonus). Planned income
 * is global like budgets: the same lines apply to every month they are active in. A blank [id] means
 * "not saved yet".
 *
 * [monthOfYear] is set exactly for [IncomeFrequency.YEARLY] lines, [yearMonth] exactly for
 * [IncomeFrequency.ONCE] lines, and [skipMonths] (month numbers 1..12 not paid) only for
 * [IncomeFrequency.MONTHLY] lines.
 */
data class PlannedIncome(
    val id: String,
    val label: String,
    val amountCents: Long,
    val order: Int,
    val basis: IncomeBasis = IncomeBasis.FIXED,
    val frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    val monthOfYear: Int? = null,
    val yearMonth: YearMonth? = null,
    val skipMonths: Set<Int> = emptySet(),
)
