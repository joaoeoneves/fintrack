package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** One month of a year's income outlook: [cents] were received ([received]) or are still expected. */
data class MonthOutlook(
    val month: YearMonth,
    val cents: Long,
    val received: Boolean,
    /** The current month: [cents] is [receivedPartCents] already received plus what is still expected. */
    val partial: Boolean = false,
    val receivedPartCents: Long = 0L,
)

/** A calendar year's income: received so far, plus what the plan expects for the months after today. */
data class YearIncomeOutlook(
    val months: List<MonthOutlook>,
) {
    val receivedCents: Long get() = months.sumOf { if (it.received) it.cents else it.receivedPartCents }
    val expectedCents: Long get() = months.sumOf { if (it.received) 0L else it.cents - it.receivedPartCents }
    val totalCents: Long get() = receivedCents + expectedCents
}

/**
 * The income outlook of [year]: each month up to [today] shows what was received ([receivedByMonth]), each later month
 * what the planned [lines] expect (versions per month, working days of [incomeMonth]). The current month is partial
 * (received so far plus the expected amount of the lines not in [receivedLineIds] yet) only while such a line is
 * still pending; once everything due is received it is shown like a past month.
 */
@Suppress("LongParameterList") // the year's plain inputs; grouping them would only move the list
fun yearIncomeOutlook(
    year: Int,
    lines: List<PlannedIncome>,
    incomeMonth: IncomeMonth,
    receivedByMonth: Map<YearMonth, Long>,
    today: YearMonth,
    receivedLineIds: Set<String> = emptySet(),
): YearIncomeOutlook =
    YearIncomeOutlook(
        (1..MONTHS_IN_YEAR).map { value ->
            val month = YearMonth.of(year, value)
            if (month == today) {
                currentMonthOutlook(month, lines, incomeMonth, receivedByMonth[month] ?: 0L, receivedLineIds)
            } else if (month < today) {
                MonthOutlook(month, receivedByMonth[month] ?: 0L, received = true)
            } else {
                MonthOutlook(month, incomeRowsIn(lines, month, incomeMonth).sumOf { it.cents }, received = false)
            }
        },
    )

private fun currentMonthOutlook(
    month: YearMonth,
    lines: List<PlannedIncome>,
    incomeMonth: IncomeMonth,
    got: Long,
    receivedLineIds: Set<String>,
): MonthOutlook {
    val pending = lines.filter { it.id !in receivedLineIds }.map { incomeMonth.amountIn(it, month) }.filter { it > 0L }
    return if (pending.isEmpty()) {
        MonthOutlook(month, got, received = true)
    } else {
        MonthOutlook(month, got + pending.sum(), received = false, partial = true, receivedPartCents = got)
    }
}

private const val MONTHS_IN_YEAR = 12
