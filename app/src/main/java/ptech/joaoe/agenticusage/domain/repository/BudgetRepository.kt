package ptech.joaoe.agenticusage.domain.repository

import kotlinx.coroutines.flow.Flow
import ptech.joaoe.agenticusage.domain.model.Budget
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>

    suspend fun setBudget(
        category: ExpenseCategory,
        limitCents: Long,
    ): Result<Unit>
}
