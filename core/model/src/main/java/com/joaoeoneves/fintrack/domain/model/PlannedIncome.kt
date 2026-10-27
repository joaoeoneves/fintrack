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
 * What a planned income line is set aside for: a single category or every category of a group. The id is a
 * category id (built-in name or document id) or a category group document id.
 */
sealed interface Earmark {
    val id: String

    data class Category(
        override val id: String,
    ) : Earmark

    data class Group(
        override val id: String,
    ) : Earmark
}

/**
 * One line of expected income in the plan (e.g. salary, meal allowance, holiday bonus). Planned income
 * is global like budgets: the same lines apply to every month they are active in. A blank [id] means
 * "not saved yet".
 *
 * [monthOfYear] is set exactly for [IncomeFrequency.YEARLY] lines, [yearMonth] exactly for
 * [IncomeFrequency.ONCE] lines, and [skipMonths] (month numbers 1..12 not paid) only for
 * [IncomeFrequency.MONTHLY] lines. A monthly line with an [earmark] feeds a pot (see [potsOf]) instead of the
 * shared base. A non-null [managedKey] marks a line generated from the salary profile: its amount, basis and
 * schedule follow the profile, only the label and earmark are the user's.
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
    val earmark: Earmark? = null,
    val managedKey: String? = null,
)
