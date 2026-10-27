package com.joaoeoneves.fintrack.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Monday to Friday days of [month] that are not in [holidays]; a holiday on a weekend counts once (not at all). */
fun workingDaysIn(
    month: YearMonth,
    holidays: Set<LocalDate> = portugueseNationalHolidays(month.year),
): Int = weekdaysOf(month).count { it !in holidays }

/** How many of [month]'s Monday to Friday days are national holidays (for "N holidays" captions). */
fun holidayWeekdaysIn(
    month: YearMonth,
    holidays: Set<LocalDate> = portugueseNationalHolidays(month.year),
): Int = weekdaysOf(month).count { it in holidays }

/** The working days to use for [month]: the user's [override] when set, else the calendar count. */
fun effectiveWorkingDays(
    month: YearMonth,
    override: Int?,
): Int = override ?: workingDaysIn(month)

private fun weekdaysOf(month: YearMonth): List<LocalDate> =
    (1..month.lengthOfMonth())
        .map { month.atDay(it) }
        .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
