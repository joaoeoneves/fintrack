package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Per-category budget limits kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalBudgetRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
    ) : BudgetRepository {
        override fun observeBudgets(): Flow<List<Budget>> = store.data.map { doc -> doc.budgets.map { it.toDomain() } }

        override suspend fun setBudget(
            categoryId: CategoryId,
            limitCents: Long,
        ): Result<Unit> =
            store.update { doc ->
                validateBudget(categoryId.value, limitCents, knownCategoryIds(doc))
                val record = LocalBudget(categoryId.value, limitCents)
                doc.copy(budgets = doc.budgets.upserted(record) { it.categoryId == record.categoryId }) to Unit
            }

        override suspend fun clearBudget(categoryId: CategoryId): Result<Unit> =
            store.update { doc ->
                doc.copy(budgets = doc.budgets.filterNot { it.categoryId == categoryId.value }) to Unit
            }
    }
