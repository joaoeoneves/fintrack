package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface MonthSettingsRepository {
    /** The months that have settings, in any order. */
    fun observeMonthSettings(): Flow<List<MonthSettings>>

    /** Sets the days off taken in [month] to [days] (0 clears them). */
    suspend fun setDaysOff(
        month: YearMonth,
        days: Int,
    ): Result<Unit>
}
