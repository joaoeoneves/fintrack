package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory [BudgetRepository] implementation intended for tests. Not wired via Hilt; construct
 * and pass it directly where a fake repository is needed.
 */
class FakeBudgetRepository(
    initialBudgets: List<Budget> = emptyList(),
    var nextObserveBudgetsError: Throwable? = null,
) : BudgetRepository {
    private val budgetsFlow = MutableStateFlow(initialBudgets)

    override fun observeBudgets(): Flow<List<Budget>> {
        nextObserveBudgetsError?.let { error -> return flow { throw error } }
        return budgetsFlow
    }

    override suspend fun setBudget(
        category: ExpenseCategory,
        limitCents: Long,
    ): Result<Unit> {
        val current = budgetsFlow.value
        budgetsFlow.value =
            if (current.any { it.category == category }) {
                current.map { if (it.category == category) it.copy(limitCents = limitCents) else it }
            } else {
                current + Budget(category = category, limitCents = limitCents)
            }
        return Result.success(Unit)
    }
}
