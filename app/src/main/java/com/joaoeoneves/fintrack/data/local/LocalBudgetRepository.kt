package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
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

        @Suppress("UnusedParameter") // Versions are persisted by the data layer.
        override suspend fun setBudget(
            categoryId: CategoryId,
            limitCents: Long,
            skipMonths: Set<Int>,
            effectiveFrom: YearMonth,
        ): Result<Unit> =
            store.update { doc ->
                validateBudget(categoryId.value, limitCents, knownCategoryIds(doc), skipMonths)
                // Like the merge write of the signed-in store, the start month and versions are kept.
                val existing = doc.budgets.find { it.categoryId == categoryId.value }
                val record =
                    LocalBudget(
                        categoryId.value,
                        limitCents,
                        skipMonths.sorted(),
                        existing?.startMonth,
                        existing?.versions.orEmpty(),
                    )
                doc.copy(budgets = doc.budgets.upserted(record) { it.categoryId == record.categoryId }) to Unit
            }

        override suspend fun clearBudget(categoryId: CategoryId): Result<Unit> =
            store.update { doc ->
                doc.copy(budgets = doc.budgets.filterNot { it.categoryId == categoryId.value }) to Unit
            }

        override suspend fun moveBudget(
            source: CategoryId,
            target: CategoryId,
        ): Result<Boolean> =
            store.update { doc ->
                require(target.value in knownCategoryIds(doc)) { "Unknown category ${target.value}" }
                val sourceBudget = doc.budgets.find { it.categoryId == source.value }
                val withoutSource = doc.budgets.filterNot { it.categoryId == source.value }
                val budgets =
                    if (sourceBudget == null || withoutSource.any { it.categoryId == target.value }) {
                        withoutSource
                    } else {
                        withoutSource + sourceBudget.copy(categoryId = target.value)
                    }
                doc.copy(budgets = budgets) to (sourceBudget != null)
            }
    }
