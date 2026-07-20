package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ExpenseRepository {
    fun observeExpenses(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Expense>>

    suspend fun addExpense(expense: Expense): Result<String>

    suspend fun updateExpense(expense: Expense): Result<Unit>

    suspend fun deleteExpense(id: String): Result<Unit>

    suspend fun getExpense(id: String): Result<Expense?>

    suspend fun getAllExpenses(): Result<List<Expense>>

    suspend fun addExpenses(expenses: List<Expense>): Result<Int>
}
