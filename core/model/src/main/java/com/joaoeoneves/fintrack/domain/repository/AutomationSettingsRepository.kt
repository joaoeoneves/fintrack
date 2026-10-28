package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface AutomationSettingsRepository {
    /** The automation state; the defaults (everything off) while nothing is stored. */
    fun observeAutomationSettings(): Flow<AutomationSettings>

    /**
     * Sets the per-line cursors in [set], removes those of [remove] and, with [clearLegacyPayday], switches the
     * legacy payday flag off.
     */
    suspend fun updateLineCursors(
        set: Map<String, YearMonth> = emptyMap(),
        remove: Set<String> = emptySet(),
        clearLegacyPayday: Boolean = false,
    ): Result<Unit>

    /** Stores [incomes] and moves the line [cursors] in one atomic write (an empty list only moves them). */
    suspend fun recordPayday(
        incomes: List<Income>,
        cursors: Map<String, YearMonth>,
    ): Result<Unit>
}
