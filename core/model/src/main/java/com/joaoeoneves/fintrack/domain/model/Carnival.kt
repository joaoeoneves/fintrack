package com.joaoeoneves.fintrack.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Carnival Tuesday of [month]'s year when it falls on a weekday of [month], else `null`. It is an optional
 * holiday: offered as a "day off" shortcut, never subtracted from the working days on its own.
 */
fun carnivalWeekdayIn(month: YearMonth): LocalDate? =
    carnivalTuesday(month.year).takeIf {
        YearMonth.from(it) == month && it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY
    }
