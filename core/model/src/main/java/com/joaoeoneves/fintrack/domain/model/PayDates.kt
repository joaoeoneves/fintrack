package com.joaoeoneves.fintrack.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The day this line pays in [month]: its fixed [PlannedIncome.payDay] (clamped to the month's length first) or the
 * last working day. With [PlannedIncome.payDayEarlierIfClosed] a fixed day on a weekend or national holiday moves to
 * the previous working day (which can fall in the previous month).
 */
fun PlannedIncome.payDateIn(month: YearMonth): LocalDate {
    val fixed = payDay?.let { month.atDay(it.coerceIn(1, month.lengthOfMonth())) } ?: return paydayOf(month)
    return if (payDayEarlierIfClosed) previousWorkingDayOrSame(fixed) else fixed
}

/** [date] itself when it is a working day, else the closest earlier one (no weekends or national holidays). */
fun previousWorkingDayOrSame(date: LocalDate): LocalDate {
    val days = generateSequence(date) { it.minusDays(1) }
    return days.first { it.isWorkingDay() }
}

private fun LocalDate.isWorkingDay(): Boolean =
    dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY && this !in portugueseNationalHolidays(year)

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
    // A shifted pay day can fall in the month before the one it pays, so next month's may already be over.
    return when {
        line.payDateIn(current.plusMonths(1)) <= today -> current.plusMonths(1)
        line.payDateIn(current) <= today -> current
        else -> current.minusMonths(1)
    }
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
    val last = YearMonth.from(today).plusMonths(1) // an earlier-if-closed pay day can fall in the month before
    return generateSequence(cursor.plusMonths(1)) { it.plusMonths(1) }
        .takeWhile { it <= last }
        .filter { line.payDateIn(it) <= today }
        .toList()
        .takeLast(cap)
}

/** The single day every paying row (cents > 0) of [rows] pays in [month]; `null` when they differ or none pays. */
fun commonPayDateIn(
    rows: List<IncomeRow>,
    month: YearMonth,
): LocalDate? =
    rows
        .filter { it.cents > 0L }
        .map { it.payDateIn(month) }
        .distinct()
        .singleOrNull()
