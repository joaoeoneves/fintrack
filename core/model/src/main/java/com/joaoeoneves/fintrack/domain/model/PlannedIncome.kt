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
 * [IncomeFrequency.MONTHLY] lines. A non-null [managedKey] marks a line generated from the salary profile: its
 * amount, basis and schedule follow the profile, only the label is the user's.
 *
 * With [autoRecord] on, the line's income is recorded by the app itself on its pay date (see `RecordPayday`);
 * [payDay] is that day of the month (1..31, clamped to the month's length; with [payDayEarlierIfClosed] a
 * weekend or holiday moves it to the previous working day) or `null` for the month's last working day.
 *
 * [startMonth] is the first plan month the line applies in (null = always); [versions] hold the amount history
 * ([amountCents] is the latest); see [asOf].
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
    val managedKey: String? = null,
    val autoRecord: Boolean = false,
    val payDay: Int? = null,
    val payDayEarlierIfClosed: Boolean = false,
    val startMonth: YearMonth? = null,
    val versions: List<AmountVersion> = emptyList(),
)

/**
 * This line as it is in plan [month]: null before its [PlannedIncome.startMonth], otherwise with the amount in
 * force that month per [versions] (a null version amount keeps the current amount).
 */
fun PlannedIncome.asOf(month: YearMonth): PlannedIncome? =
    if (startMonth != null && month < startMonth) {
        null
    } else {
        copy(amountCents = versions.amountAt(month, amountCents) ?: amountCents)
    }

/** The lines that exist in plan [month], each resolved with [asOf]. */
fun List<PlannedIncome>.asOf(month: YearMonth): List<PlannedIncome> = mapNotNull { it.asOf(month) }

/** This line with its amount changed to [newAmount] from plan [month] on (see [editedVersions]). */
fun PlannedIncome.editedFrom(
    month: YearMonth,
    newAmount: Long,
): PlannedIncome {
    val edit = editedVersions(startMonth, versions, amountCents, month, newAmount)
    return if (edit.changedFrom(startMonth, versions)) {
        copy(amountCents = edit.latest ?: amountCents, startMonth = edit.startMonth, versions = edit.versions)
    } else {
        this
    }
}

/** This (new) line starting in plan [month], with no amount history. */
fun PlannedIncome.startingAt(month: YearMonth): PlannedIncome = copy(startMonth = month, versions = emptyList())
