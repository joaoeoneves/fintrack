package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Expenses kept in the guest-mode [LocalDataStore]. Like the Firestore repository, expenses carry
 * only built-in (or unknown placeholder) categories; the category-resolving decorator maps the rest.
 * Writes are validated with the same rules firestore.rules enforces.
 */
@Singleton
class LocalExpenseRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : ExpenseRepository {
        override fun observeExpenses(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Expense>> =
            store.data.map { doc ->
                doc.expenses
                    .mapNotNull { it.toDomainOrNull() }
                    .filter { it.date >= startInclusive && it.date < endExclusive }
                    .sortedBy { it.date }
            }

        override suspend fun addExpense(expense: Expense): Result<String> =
            store.update { doc ->
                val record = expense.toLocal().let { it.copy(id = it.id.ifBlank { idGenerator.newId() }) }
                validateExpense(record, knownCategoryIds(doc))
                doc.copy(expenses = doc.expenses.upserted(record) { it.id == record.id }) to record.id
            }

        override suspend fun updateExpense(expense: Expense): Result<Unit> =
            store.update { doc ->
                val record = expense.toLocal()
                require(doc.expenses.any { it.id == record.id }) { "Expense ${record.id} does not exist" }
                validateExpense(record, knownCategoryIds(doc))
                doc.copy(expenses = doc.expenses.map { if (it.id == record.id) record else it }) to Unit
            }

        override suspend fun deleteExpense(id: String): Result<Unit> =
            store.update { doc -> doc.copy(expenses = doc.expenses.filterNot { it.id == id }) to Unit }

        override suspend fun getExpense(id: String): Result<Expense?> =
            runCatchingCancellable {
                store.data
                    .first()
                    .expenses
                    .find { it.id == id }
                    ?.toDomainOrNull()
            }

        override suspend fun getAllExpenses(): Result<List<Expense>> =
            runCatchingCancellable {
                store.data
                    .first()
                    .expenses
                    .mapNotNull { it.toDomainOrNull() }
            }

        override suspend fun addExpenses(expenses: List<Expense>): BulkAddResult =
            store
                .update { doc ->
                    val known = knownCategoryIds(doc)
                    val records =
                        expenses.map { expense ->
                            expense.toLocal().copy(id = idGenerator.newId()).also { validateExpense(it, known) }
                        }
                    doc.copy(expenses = doc.expenses + records) to records.size
                }.fold(
                    onSuccess = { BulkAddResult(it, null) },
                    onFailure = { BulkAddResult(0, it) },
                )

        override suspend fun hasExpenseWithCategory(categoryId: CategoryId): Result<Boolean> =
            runCatchingCancellable {
                store.data
                    .first()
                    .expenses
                    .any { it.categoryId == categoryId.value }
            }
    }
