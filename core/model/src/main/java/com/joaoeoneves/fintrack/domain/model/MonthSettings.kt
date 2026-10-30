package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** Per-month plan settings: the vacation/[daysOff] taken that month (subtracted from the calendar working days). */
data class MonthSettings(
    val month: YearMonth,
    val daysOff: Int = 0,
) {
    /** Whether nothing is set, i.e. the month needs no stored document. */
    val isBlank: Boolean get() = daysOff == 0
}
