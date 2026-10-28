package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface AutomationSettingsRepository {
    /** The automation switches; the defaults (everything off) while nothing is stored. */
    fun observeAutomationSettings(): Flow<AutomationSettings>

    /** Switches the payday automation on or off; [cursor], when given, replaces the stored cursor (on enabling). */
    suspend fun setPaydayEnabled(
        enabled: Boolean,
        cursor: YearMonth? = null,
    ): Result<Unit>

    /** Stores [incomes] and moves the payday cursor to [month] in one atomic write (an empty list only moves it). */
    suspend fun recordPayday(
        month: YearMonth,
        incomes: List<Income>,
    ): Result<Unit>
}
