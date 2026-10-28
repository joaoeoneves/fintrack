package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** The day this line pays in [month]: its fixed [PlannedIncome.payDay] (clamped) or the last working day. */
fun PlannedIncome.payDateIn(month: YearMonth): LocalDate =
    payDay?.let { month.atDay(it.coerceIn(1, month.lengthOfMonth())) } ?: paydayOf(month)

/** The day this row pays in [month]: the latest pay date of its lines active that month (any line when none is). */
fun IncomeRow.payDateIn(month: YearMonth): LocalDate {
    val active = lines.filter { it.isActiveIn(month) }.ifEmpty { lines }
    return active.maxOfOrNull { it.payDateIn(month) } ?: paydayOf(month)
}

/** The cursor to start from when [line] is switched on [today]: this month if its pay day is over, else last. */
fun initialLineCursor(
    line: PlannedIncome,
    today: LocalDate,
): YearMonth {
    val current = YearMonth.from(today)
    return if (line.payDateIn(current) <= today) current else current.minusMonths(1)
}

/**
 * The months whose pay day for [line] has come and gone since [cursor] (exclusive), oldest first, at most the last
 * [cap]. Empty while the cursor is `null` (the line was never switched on).
 */
fun lineMonthsDue(
    line: PlannedIncome,
    cursor: YearMonth?,
    today: LocalDate,
    cap: Int = PAYDAY_CATCH_UP_MONTHS,
): List<YearMonth> {
    if (cursor == null) return emptyList()
    val current = YearMonth.from(today)
    return generateSequence(cursor.plusMonths(1)) { it.plusMonths(1) }
        .takeWhile { it <= current }
        .filter { line.payDateIn(it) <= today }
        .toList()
        .takeLast(cap)
}
