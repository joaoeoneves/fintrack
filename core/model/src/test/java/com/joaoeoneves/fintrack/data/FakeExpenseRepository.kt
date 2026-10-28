package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * In-memory [ExpenseRepository] implementation intended for tests. Not wired via Hilt; construct
 * and pass it directly where a fake repository is needed.
 */
class FakeExpenseRepository(
    initialExpenses: List<Expense> = emptyList(),
    var nextObserveExpensesError: Throwable? = null,
    var nextGetExpenseError: Throwable? = null,
    var nextAddExpensesFailure: Throwable? = null,
    var addExpensesFailureAfterCount: Int = Int.MAX_VALUE,
    var nextHasExpenseWithCategoryError: Throwable? = null,
) : ExpenseRepository {
    private val expensesFlow = MutableStateFlow(initialExpenses)

    var nextCountExpensesError: Throwable? = null
    var nextMoveExpensesError: Throwable? = null

    /** Resolves the [Category] an expense gets when moved to an id; defaults to built-ins, else unknown. */
    var categoryLookup: (CategoryId) -> Category = { id ->
        BuiltInCategories.all.find { it.id == id } ?: Category.unknown(id)
    }

    override fun observeExpenses(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Expense>> {
        nextObserveExpensesError?.let { error -> return flow { throw error } }
        return expensesFlow.map { expenses ->
            expenses.filter { it.date >= startInclusive && it.date < endExclusive }
        }
    }

    override suspend fun addExpense(expense: Expense): Result<String> {
        val id = expense.id.ifBlank { UUID.randomUUID().toString() }
        val toStore = expense.copy(id = id)
        expensesFlow.value = expensesFlow.value + toStore
        return Result.success(id)
    }

    override suspend fun updateExpense(expense: Expense): Result<Unit> {
        val current = expensesFlow.value
        if (current.none { it.id == expense.id }) {
            return Result.failure(NoSuchElementException("Expense with id ${expense.id} not found"))
        }
        expensesFlow.value = current.map { if (it.id == expense.id) expense else it }
        return Result.success(Unit)
    }

    override suspend fun deleteExpense(id: String): Result<Unit> {
        // Matches real Firestore: deleting a missing document is a successful no-op.
        expensesFlow.value = expensesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun getExpense(id: String): Result<Expense?> {
        nextGetExpenseError?.let { error ->
            nextGetExpenseError = null
            return Result.failure(error)
        }
        return Result.success(expensesFlow.value.find { it.id == id })
    }

    override suspend fun getAllExpenses(): Result<List<Expense>> = Result.success(expensesFlow.value)

    override suspend fun addExpenses(expenses: List<Expense>): BulkAddResult {
        val failure = nextAddExpensesFailure
        if (failure != null) {
            nextAddExpensesFailure = null
            val committed = expenses.take(addExpensesFailureAfterCount)
            val toStore = committed.map { it.copy(id = UUID.randomUUID().toString()) }
            expensesFlow.value = expensesFlow.value + toStore
            return BulkAddResult(committed.size, failure)
        }
        val toStore = expenses.map { it.copy(id = UUID.randomUUID().toString()) }
        expensesFlow.value = expensesFlow.value + toStore
        return BulkAddResult(expenses.size, null)
    }

    override suspend fun hasExpenseWithCategory(categoryId: CategoryId): Result<Boolean> {
        nextHasExpenseWithCategoryError?.let { error ->
            nextHasExpenseWithCategoryError = null
            return Result.failure(error)
        }
        return Result.success(expensesFlow.value.any { it.category.id == categoryId })
    }

    override suspend fun countExpensesWithCategory(categoryId: CategoryId): Result<Int> {
        nextCountExpensesError?.let { error ->
            nextCountExpensesError = null
            return Result.failure(error)
        }
        return Result.success(expensesFlow.value.count { it.category.id == categoryId })
    }

    override suspend fun moveExpensesToCategory(
        source: CategoryId,
        target: CategoryId,
    ): Result<Int> {
        nextMoveExpensesError?.let { error ->
            nextMoveExpensesError = null
            return Result.failure(error)
        }
        val targetCategory = categoryLookup(target)
        val moving = expensesFlow.value.count { it.category.id == source }
        expensesFlow.value =
            expensesFlow.value.map { if (it.category.id == source) it.copy(category = targetCategory) else it }
        return Result.success(moving)
    }
}
