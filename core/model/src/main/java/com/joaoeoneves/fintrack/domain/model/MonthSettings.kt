package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Per-month plan settings: a manual working-days count replacing the calendar computation, and the
 * vacation/[daysOff] taken that month (subtracted from the automatic count). Either may be unset.
 */
data class MonthSettings(
    val month: YearMonth,
    val workingDaysOverride: Int?,
    val daysOff: Int = 0,
) {
    /** Whether nothing is set, i.e. the month needs no stored document. */
    val isBlank: Boolean get() = workingDaysOverride == null && daysOff == 0
}
