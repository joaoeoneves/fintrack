package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Per-month plan settings: the days off taken that month, by type. Vacation days count against the year's
 * vacation allowance; other days (sick, personal...) only reduce the month's working days. [daysOff] is the total.
 */
data class MonthSettings(
    val month: YearMonth,
    val vacationDays: Int = 0,
    val otherDays: Int = 0,
) {
    /** All days off taken in the month, whatever their type (subtracted from the calendar working days). */
    val daysOff: Int get() = vacationDays + otherDays

    /** Whether nothing is set, i.e. the month needs no stored document. */
    val isBlank: Boolean get() = daysOff == 0
}

/** Most months one days-off edit may cover. */
const val MAX_DAYS_OFF_MONTHS = 24

/** Throws [IllegalArgumentException] unless the per-type counts and the month count are valid for a days-off write. */
fun requireValidDaysOff(
    months: Collection<YearMonth>,
    vacation: Int,
    other: Int,
) {
    require(months.size in 1..MAX_DAYS_OFF_MONTHS) { "Days off cover 1..$MAX_DAYS_OFF_MONTHS months" }
    require(vacation in 0..MAX_DAYS_OFF && other in 0..MAX_DAYS_OFF) {
        "Each days-off count must be 0..$MAX_DAYS_OFF"
    }
    require(vacation + other <= MAX_DAYS_OFF) { "Days off must total at most $MAX_DAYS_OFF" }
}
