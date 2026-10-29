package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.ZoneId

/**
 * One month of the history, standing alone: [incomeCents] is what FUNDS the month (see [fundingMonthOf]),
 * [spendingCents] the net outflow of SPENDING categories by expense date and [savedCents] the net outflow of SAVING
 * categories (the same definition as the Savings tab's "saved this year").
 */
data class MonthTotals(
    val month: YearMonth,
    val incomeCents: Long,
    val spendingCents: Long,
    val savedCents: Long,
) {
    /** Whether anything at all happened in the month. */
    val hasData: Boolean get() = incomeCents != 0L || spendingCents != 0L || savedCents != 0L
}

/**
 * The totals of every month in [months], in the given order. Incomes are attributed by the month they fund, expenses
 * by their own date in [zone]; refunds net; transactions outside [months] are ignored.
 */
@Suppress("LongParameterList") // The inputs of one month attribution, mirrors the other *Of functions.
fun monthlyTotalsOf(
    months: List<YearMonth>,
    expenses: List<Expense>,
    income: List<Income>,
    categories: List<Category>,
    lines: List<PlannedIncome>,
    funding: IncomeFunding,
    zone: ZoneId,
): List<MonthTotals> {
    val expensesByMonth = expenses.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val incomeByMonth = income.groupBy { fundingMonthOf(it, lines, funding, zone) }
    return months.map { month ->
        val summary = cashFlowSummaryOf(expensesByMonth[month].orEmpty(), incomeByMonth[month].orEmpty(), categories)
        MonthTotals(month, summary.incomeCents, summary.spendingCents, summary.savedCents)
    }
}

/**
 * Net spending per category and month (refunds net, each month floored at 0), one entry per category that has an
 * expense in [months]; each list has one value per month, in the order of [months]. SAVING categories are included:
 * callers filter by kind as needed.
 */
fun categoryMonthlyOf(
    months: List<YearMonth>,
    expenses: List<Expense>,
    categories: List<Category>,
    zone: ZoneId,
): Map<CategoryId, List<Long>> {
    val byMonth = expenses.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val ids =
        (categories.map { it.id } + expenses.map { it.category.id })
            .distinct()
            .filter { id -> months.any { m -> byMonth[m].orEmpty().any { it.category.id == id } } }
    return ids.associateWith { id ->
        months.map { m ->
            byMonth[m]
                .orEmpty()
                .filter { it.category.id == id }
                .netCents()
                .coerceAtLeast(0L)
        }
    }
}

/**
 * This month's spending against last month's ([previousCents], `null` without data) and against the average of the
 * up-to-[averageMonths] earlier months that have data ([averageCents], `null` when none).
 */
data class SpendingComparison(
    val currentCents: Long,
    val previousCents: Long?,
    val averageCents: Long?,
    val averageMonths: Int,
)

/** Default number of earlier months the average looks at. */
const val COMPARISON_WINDOW_MONTHS = 6

/** Compares [month]'s spending in [totals] with the previous month and the average of the [window] before it. */
fun spendingComparisonOf(
    totals: List<MonthTotals>,
    month: YearMonth,
    window: Int = COMPARISON_WINDOW_MONTHS,
): SpendingComparison {
    val byMonth = totals.associateBy { it.month }
    val earlier =
        (1..window).mapNotNull { back -> byMonth[month.minusMonths(back.toLong())]?.takeIf { it.hasData } }
    val previous = byMonth[month.minusMonths(1)]?.takeIf { it.hasData }
    return SpendingComparison(
        currentCents = byMonth[month]?.spendingCents ?: 0L,
        previousCents = previous?.spendingCents,
        averageCents = earlier.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.spendingCents } / list.size },
        averageMonths = earlier.size,
    )
}

/**
 * The value at [index] of [values] against the average of the up-to-[window] earlier entries that have data (non-zero);
 * `null` when no earlier entry has data. [SpendingComparison.previousCents] is the entry right before [index].
 */
fun seriesComparisonOf(
    values: List<Long>,
    index: Int,
    window: Int = COMPARISON_WINDOW_MONTHS,
): SpendingComparison {
    val earlier = (1..window).mapNotNull { back -> values.getOrNull(index - back)?.takeIf { it != 0L } }
    return SpendingComparison(
        currentCents = values.getOrNull(index) ?: 0L,
        previousCents = values.getOrNull(index - 1)?.takeIf { it != 0L },
        averageCents = earlier.takeIf { it.isNotEmpty() }?.let { list -> list.sum() / list.size },
        averageMonths = earlier.size,
    )
}

/** Income, spending and saving of [year] from January up to the month given to [yearToDateOf]. */
data class YearToDate(
    val year: Int,
    val spentCents: Long,
    val incomeCents: Long,
    val savedCents: Long,
)

/** The sums of the months of [totals] in [upTo]'s year, January through [upTo]. */
fun yearToDateOf(
    totals: List<MonthTotals>,
    upTo: YearMonth,
): YearToDate {
    val inYear = totals.filter { it.month.year == upTo.year && it.month <= upTo }
    return YearToDate(
        year = upTo.year,
        spentCents = inYear.sumOf { it.spendingCents },
        incomeCents = inYear.sumOf { it.incomeCents },
        savedCents = inYear.sumOf { it.savedCents },
    )
}

/** The percentage change from [base] to [current], rounded; `null` when [base] is missing or not positive. */
fun percentChange(
    current: Long,
    base: Long?,
): Int? = base?.takeIf { it > 0L }?.let { Math.round((current - it) * PERCENT / it).toInt() }

private const val PERCENT = 100.0
