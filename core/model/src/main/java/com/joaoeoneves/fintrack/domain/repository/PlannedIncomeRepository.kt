package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.flow.Flow

interface PlannedIncomeRepository {
    /** The expected-income lines, sorted by order then id. */
    fun observePlannedIncome(): Flow<List<PlannedIncome>>

    /** Creates or replaces a line (a blank id creates one); returns the line's id. */
    suspend fun savePlannedIncome(line: PlannedIncome): Result<String>

    /** Deletes a line; a missing one is a no-op success. */
    suspend fun deletePlannedIncome(id: String): Result<Unit>
}
