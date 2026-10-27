package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.MAX_WORKING_DAYS
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import java.time.YearMonth

/** `null` when the record's month is unparsable. */
internal fun LocalMonthSettings.toDomainOrNull(): MonthSettings? =
    parseYearMonthOrNull(month)?.let { MonthSettings(month = it, workingDaysOverride = workingDays, daysOff = daysOff) }

internal fun MonthSettings.toLocal(): LocalMonthSettings =
    LocalMonthSettings(month = month.toString(), workingDays = workingDaysOverride, daysOff = daysOff)

internal fun localMonthSettingsOf(
    month: YearMonth,
    workingDays: Int?,
    daysOff: Int,
): LocalMonthSettings = LocalMonthSettings(month = month.toString(), workingDays = workingDays, daysOff = daysOff)

/** Mirror of the firestore.rules check, plus "something is set"; throws [IllegalArgumentException] for a violation. */
internal fun validateMonthSettings(record: LocalMonthSettings) {
    require(parseYearMonthOrNull(record.month) != null) { "Month settings id must be yyyy-MM" }
    require(record.workingDays == null || record.workingDays in 0..MAX_WORKING_DAYS) {
        "Working days must be 0..$MAX_WORKING_DAYS"
    }
    require(record.daysOff in 0..MAX_DAYS_OFF) { "Days off must be 0..$MAX_DAYS_OFF" }
    require(record.workingDays != null || record.daysOff > 0) { "Month settings must set something" }
}
