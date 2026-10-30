package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
@Suppress("TooManyFunctions") // One override per ExpenseRepository operation.
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

        override fun observeHasAnyExpense(): Flow<Boolean> {
            val hasAny = store.data.map { doc -> doc.expenses.isNotEmpty() }
            return hasAny.distinctUntilChanged()
        }

        override fun observeNeedingReview(): Flow<List<Expense>> =
            store.data.map { doc ->
                doc.expenses
                    .filter { it.needsReview }
                    .mapNotNull { it.toDomainOrNull() }
                    .sortedBy { it.date }
            }

        override suspend fun markReviewed(ids: Collection<String>): Result<Unit> =
            store.update { doc ->
                val wanted = ids.toSet()
                val expenses = doc.expenses.map { if (it.id in wanted) it.copy(needsReview = false) else it }
                doc.copy(expenses = expenses) to Unit
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

        override suspend fun addExpensesKeepingIds(expenses: List<Expense>): Result<List<String>> =
            store.update { doc ->
                val known = knownCategoryIds(doc)
                val records =
                    expenses.map { expense ->
                        expense.toLocal().let { it.copy(id = it.id.ifBlank { idGenerator.newId() }) }.also {
                            validateExpense(it, known)
                        }
                    }
                val added = records.map { it.id }.toSet()
                doc.copy(expenses = doc.expenses.filterNot { it.id in added } + records) to records.map { it.id }
            }

        override suspend fun updateExpenses(expenses: List<Expense>): Result<Unit> =
            store.update { doc ->
                val known = knownCategoryIds(doc)
                val records = expenses.map { it.toLocal() }
                val existing = doc.expenses.mapTo(mutableSetOf()) { it.id }
                records.forEach { record ->
                    require(record.id in existing) { "Expense ${record.id} does not exist" }
                    validateExpense(record, known)
                }
                val byId = records.associateBy { it.id }
                doc.copy(expenses = doc.expenses.map { byId[it.id] ?: it }) to Unit
            }

        override suspend fun deleteExpenses(ids: Collection<String>): Result<Unit> =
            store.update { doc ->
                val wanted = ids.toSet()
                doc.copy(expenses = doc.expenses.filterNot { it.id in wanted }) to Unit
            }

        override fun observeLinkedRefunds(): Flow<List<Expense>> =
            store.data.map { doc ->
                doc.expenses
                    .filter { it.isRefund && it.refundOf != null }
                    .mapNotNull { it.toDomainOrNull() }
            }

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

        override suspend fun expenseIdsBetween(
            firstId: String,
            lastId: String,
        ): Result<Set<String>> =
            runCatchingCancellable {
                store.data
                    .first()
                    .expenses
                    .map { it.id }
                    .filter { it >= firstId && it <= lastId }
                    .toSet()
            }

        override suspend fun countExpensesWithCategory(categoryId: CategoryId): Result<Int> =
            runCatchingCancellable {
                store.data
                    .first()
                    .expenses
                    .count { it.categoryId == categoryId.value }
            }

        override suspend fun moveExpensesToCategory(
            source: CategoryId,
            target: CategoryId,
        ): Result<Int> =
            store.update { doc ->
                require(target.value in knownCategoryIds(doc)) { "Unknown category ${target.value}" }
                val moved = doc.expenses.count { it.categoryId == source.value }
                val expenses =
                    doc.expenses.map { if (it.categoryId == source.value) it.copy(categoryId = target.value) else it }
                doc.copy(expenses = expenses) to moved
            }
    }
