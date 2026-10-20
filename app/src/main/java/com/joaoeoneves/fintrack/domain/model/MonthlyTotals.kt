package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.ZoneId

/** The [CashFlowSummary] of a single calendar [month]. */
data class MonthlyTotals(
    val month: YearMonth,
    val summary: CashFlowSummary,
)

/**
 * One [MonthlyTotals] per calendar month from [from] to [to] (both inclusive), ascending. Months
 * without transactions get a zero summary; transactions are bucketed by their date in [zone], and
 * those outside the months are ignored. Empty when [from] is after [to].
 */
fun monthlyTotalsOf(
    expenses: List<Expense>,
    income: List<Income>,
    from: YearMonth,
    to: YearMonth,
    zone: ZoneId,
): List<MonthlyTotals> {
    if (from > to) return emptyList()
    val expensesByMonth = expenses.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val incomeByMonth = income.groupBy { YearMonth.from(it.date.atZone(zone)) }
    return generateSequence(from) { month -> month.plusMonths(1).takeIf { it <= to } }
        .map { month ->
            MonthlyTotals(
                month = month,
                summary =
                    cashFlowSummaryOf(
                        expenses = expensesByMonth[month].orEmpty(),
                        income = incomeByMonth[month].orEmpty(),
                    ),
            )
        }.toList()
}
