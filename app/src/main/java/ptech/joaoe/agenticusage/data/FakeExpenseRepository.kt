package ptech.joaoe.agenticusage.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository
import java.time.Instant
import java.util.UUID

/**
 * In-memory [ExpenseRepository] implementation intended for tests. Not wired via Hilt; construct
 * and pass it directly where a fake repository is needed.
 */
class FakeExpenseRepository(
    initialExpenses: List<Expense> = emptyList(),
    var nextObserveExpensesError: Throwable? = null,
) : ExpenseRepository {
    private val expensesFlow = MutableStateFlow(initialExpenses)

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
        val current = expensesFlow.value
        if (current.none { it.id == id }) {
            return Result.failure(NoSuchElementException("Expense with id $id not found"))
        }
        expensesFlow.value = current.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun getExpense(id: String): Expense? = expensesFlow.value.find { it.id == id }

    override suspend fun getAllExpenses(): Result<List<Expense>> = Result.success(expensesFlow.value)

    override suspend fun addExpenses(expenses: List<Expense>): Result<Int> {
        val toStore = expenses.map { it.copy(id = UUID.randomUUID().toString()) }
        expensesFlow.value = expensesFlow.value + toStore
        return Result.success(expenses.size)
    }
}
