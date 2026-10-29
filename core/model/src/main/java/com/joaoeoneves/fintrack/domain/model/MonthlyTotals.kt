package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.ZoneId

/** The [CashFlowSummary] of a single calendar [month]. */
data class MonthlyTotals(
    val month: YearMonth,
    val summary: CashFlowSummary,
)

/**
 * One [MonthlyTotals] per calendar month in [months] (both ends inclusive), ascending. Months
 * without transactions get a zero summary; transactions are bucketed by their date in [zone], and
 * those outside the months are ignored. Empty when [months] is empty. Each month is summarized
 * against the [categories] catalog (see [cashFlowSummaryOf]).
 */
fun monthlyTotalsOf(
    expenses: List<Expense>,
    income: List<Income>,
    months: ClosedRange<YearMonth>,
    zone: ZoneId,
    categories: List<Category>,
): List<MonthlyTotals> {
    if (months.isEmpty()) return emptyList()
    val from = months.start
    val to = months.endInclusive
    val expensesByMonth = expenses.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val incomeByMonth = income.groupBy { it.ownerMonth(zone) }
    return generateSequence(from) { month -> month.plusMonths(1).takeIf { it <= to } }
        .map { month ->
            MonthlyTotals(
                month = month,
                summary =
                    cashFlowSummaryOf(
                        expenses = expensesByMonth[month].orEmpty(),
                        income = incomeByMonth[month].orEmpty(),
                        categories = categories,
                    ),
            )
        }.toList()
}
