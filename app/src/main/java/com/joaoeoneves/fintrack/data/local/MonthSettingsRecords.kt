package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.MonthSettings

/** `null` when the record's month is unparsable. */
internal fun LocalMonthSettings.toDomainOrNull(): MonthSettings? =
    parseYearMonthOrNull(month)?.let { MonthSettings(it, vacationDays, sickDays, otherDays) }

internal fun MonthSettings.toLocal() = LocalMonthSettings(month.toString(), vacationDays, sickDays, otherDays)

/** Mirror of the firestore.rules check, plus "something is set"; throws [IllegalArgumentException] for a violation. */
internal fun validateMonthSettings(record: LocalMonthSettings) {
    require(parseYearMonthOrNull(record.month) != null) { "Month settings id must be yyyy-MM" }
    require(record.legacyDaysOff == null) { "Legacy days off must be upgraded first" }
    val counts = listOf(record.vacationDays, record.sickDays, record.otherDays)
    require(counts.all { it in 0..MAX_DAYS_OFF }) {
        "Each days-off count must be 0..$MAX_DAYS_OFF"
    }
    require(record.totalDays in 1..MAX_DAYS_OFF) { "Days off must total 1..$MAX_DAYS_OFF" }
}
