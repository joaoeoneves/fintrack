package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface MonthSettingsRepository {
    /** The months that have settings, in any order. */
    fun observeMonthSettings(): Flow<List<MonthSettings>>

    /** Sets the working days of [month] to [days]; `null` removes the override (back to automatic). */
    suspend fun setWorkingDaysOverride(
        month: YearMonth,
        days: Int?,
    ): Result<Unit>
}
