package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>

    suspend fun setBudget(
        categoryId: CategoryId,
        limitCents: Long,
    ): Result<Unit>

    suspend fun clearBudget(categoryId: CategoryId): Result<Unit>
}
