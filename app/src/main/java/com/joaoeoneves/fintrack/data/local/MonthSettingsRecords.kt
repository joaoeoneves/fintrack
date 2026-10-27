package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_WORKING_DAYS
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import java.time.YearMonth

/** `null` when the record's month is unparsable. */
internal fun LocalMonthSettings.toDomainOrNull(): MonthSettings? =
    parseYearMonthOrNull(month)?.let { MonthSettings(month = it, workingDaysOverride = workingDays) }

internal fun localMonthSettingsOf(
    month: YearMonth,
    workingDays: Int,
): LocalMonthSettings = LocalMonthSettings(month = month.toString(), workingDays = workingDays)

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validateMonthSettings(record: LocalMonthSettings) {
    require(parseYearMonthOrNull(record.month) != null) { "Month settings id must be yyyy-MM" }
    require(record.workingDays in 0..MAX_WORKING_DAYS) { "Working days must be 0..$MAX_WORKING_DAYS" }
}
