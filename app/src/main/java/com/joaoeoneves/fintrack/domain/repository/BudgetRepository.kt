package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>

    suspend fun setBudget(
        category: ExpenseCategory,
        limitCents: Long,
    ): Result<Unit>
}
