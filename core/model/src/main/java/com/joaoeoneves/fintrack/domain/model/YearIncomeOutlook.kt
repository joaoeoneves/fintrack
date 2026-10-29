package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** One month of a year's income outlook: [cents] were received ([received]) or are still expected. */
data class MonthOutlook(
    val month: YearMonth,
    val cents: Long,
    val received: Boolean,
)

/** A calendar year's income: received so far, plus what the plan expects for the months after today. */
data class YearIncomeOutlook(
    val months: List<MonthOutlook>,
) {
    val receivedCents: Long get() = months.filter { it.received }.sumOf { it.cents }
    val expectedCents: Long get() = months.filterNot { it.received }.sumOf { it.cents }
    val totalCents: Long get() = receivedCents + expectedCents
}

/**
 * The income outlook of [year]: each month up to [today] shows what was received ([receivedByMonth]), each later month
 * what the planned [lines] expect (versions per month, working days of [incomeMonth]).
 */
fun yearIncomeOutlook(
    year: Int,
    lines: List<PlannedIncome>,
    incomeMonth: IncomeMonth,
    receivedByMonth: Map<YearMonth, Long>,
    today: YearMonth,
): YearIncomeOutlook =
    YearIncomeOutlook(
        (1..MONTHS_IN_YEAR).map { value ->
            val month = YearMonth.of(year, value)
            if (month <= today) {
                MonthOutlook(month, receivedByMonth[month] ?: 0L, received = true)
            } else {
                MonthOutlook(month, incomeRowsIn(lines, month, incomeMonth).sumOf { it.cents }, received = false)
            }
        },
    )

private const val MONTHS_IN_YEAR = 12
