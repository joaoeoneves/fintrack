package ptech.joaoe.agenticusage.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import ptech.joaoe.agenticusage.domain.model.Expense

interface ExpenseRepository {
    fun observeExpenses(startInclusive: Instant, endExclusive: Instant): Flow<List<Expense>>

    suspend fun addExpense(expense: Expense): Result<String>

    suspend fun updateExpense(expense: Expense): Result<Unit>

    suspend fun deleteExpense(id: String): Result<Unit>

    suspend fun getExpense(id: String): Expense?

    suspend fun getAllExpenses(): Result<List<Expense>>

    suspend fun addExpenses(expenses: List<Expense>): Result<Int>
}
