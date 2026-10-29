package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.ZoneId

/** How many months before the viewed one the rollover looks back at most. */
const val CARRY_LOOKBACK_MONTHS = 12L

/**
 * The month's headline numbers, computed once and shown by Home and the monthly plan alike. They add up:
 * [carryCents] + [incomeCents] - [recurringCents] - [envelopesCents] - [savingCents] = [availableCents].
 *
 * @property carryCents what earlier months left over (negative when they overspent), see [carryOverCents].
 * @property incomeCents the income basis: the larger of the expected regular income and what was received.
 * @property recurringCents recurring bills of the month (planned, or spent when higher).
 * @property envelopesCents what envelopes and loose categories set aside beyond the recurring bills.
 * @property savingCents what is planned (or already put) into savings.
 */
data class MonthFigures(
    val carryCents: Long,
    val incomeCents: Long,
    val recurringCents: Long,
    val envelopesCents: Long,
    val savingCents: Long,
) {
    val availableCents: Long get() = carryCents + incomeCents - recurringCents - envelopesCents - savingCents
}

/** The month's [MonthFigures] from its [EnvelopesSummary] and the [carryCents] brought over from earlier months. */
fun monthFiguresOf(
    summary: EnvelopesSummary,
    carryCents: Long,
): MonthFigures =
    MonthFigures(
        carryCents = carryCents,
        incomeCents = summary.incomeCents,
        recurringCents = summary.recurringCents,
        envelopesCents =
            summary.incomeCents - summary.freeCents - summary.savingCommittedCents - summary.recurringCents,
        savingCents = summary.savingCommittedCents,
    )

/** What really happened in one [month]: money received, spending and money put into savings. */
data class MonthActuals(
    val month: YearMonth,
    val incomeCents: Long,
    val spentCents: Long,
    val savedCents: Long,
) {
    /** What the month really left: received income minus spending minus savings; negative when overspent. */
    val leftoverCents: Long get() = incomeCents - spentCents - savedCents
}

/**
 * Per-month actuals of the months in [expenses] and [incomes]: [incomes] are attributed by [funding] (a salary paid
 * on the last working day funds the next month) and expenses by their date in [zone]; expense kinds come from
 * [categories]. Sorted by month.
 */
fun monthActualsOf(
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>,
    lines: List<PlannedIncome>,
    funding: IncomeFunding,
    zone: ZoneId,
): List<MonthActuals> {
    val spendByMonth = expenses.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val incomeByMonth = incomes.groupBy { fundingMonthOf(it, lines, funding, zone) }
    return (spendByMonth.keys + incomeByMonth.keys).sorted().map { month ->
        val summary = cashFlowSummaryOf(spendByMonth[month].orEmpty(), incomeByMonth[month].orEmpty(), categories)
        MonthActuals(month, summary.incomeCents, summary.spendingCents, summary.savedCents)
    }
}

/**
 * What the months before [month] carry into it: the sum of their [MonthActuals.leftoverCents], starting at the first
 * month in which any income was received (earlier months only have spending paid from money the app never saw).
 * Looks back at most [CARRY_LOOKBACK_MONTHS]; 0 when there is nothing before [month]. The result can be negative.
 */
fun carryOverCents(
    month: YearMonth,
    actuals: List<MonthActuals>,
): Long {
    val earliest = month.minusMonths(CARRY_LOOKBACK_MONTHS)
    val before = actuals.filter { it.month < month && it.month >= earliest }.sortedBy { it.month }
    val first = before.indexOfFirst { it.incomeCents > 0L }
    return if (first < 0) 0L else before.drop(first).sumOf { it.leftoverCents }
}
