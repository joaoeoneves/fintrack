package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface MonthSettingsRepository {
    /** The months that have settings, in any order. */
    fun observeMonthSettings(): Flow<List<MonthSettings>>

    /**
     * Sets the days off taken in every one of [months] (1..24 distinct-or-not months) to the given per-type counts, in
     * one atomic write. Both zero clears the months. Each count is 0..MAX_DAYS_OFF and their total at most
     * MAX_DAYS_OFF; anything else throws [IllegalArgumentException].
     */
    suspend fun setDaysOff(
        months: Collection<YearMonth>,
        vacation: Int,
        other: Int,
    ): Result<Unit>
}
