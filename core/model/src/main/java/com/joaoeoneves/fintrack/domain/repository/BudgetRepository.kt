package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>

    /** Sets the budget as a whole: [skipMonths] replaces the previous skipped months. */
    suspend fun setBudget(
        categoryId: CategoryId,
        limitCents: Long,
        skipMonths: Set<Int> = emptySet(),
    ): Result<Unit>

    suspend fun clearBudget(categoryId: CategoryId): Result<Unit>

    /**
     * Moves the budget of [source] to [target]. When [target] already has a budget it is kept and the
     * source's is dropped. Returns whether [source] had a budget. Awaited when signed in.
     */
    suspend fun moveBudget(
        source: CategoryId,
        target: CategoryId,
    ): Result<Boolean>
}
