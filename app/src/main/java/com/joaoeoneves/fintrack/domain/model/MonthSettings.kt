package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** Per-month plan settings: a manual working-days count replacing the calendar computation. */
data class MonthSettings(
    val month: YearMonth,
    val workingDaysOverride: Int?,
)
