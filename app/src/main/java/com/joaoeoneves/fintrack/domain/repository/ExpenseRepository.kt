package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Outcome of a bulk-add operation (e.g. CSV import). Firestore writes are chunked into batches,
 * and a failure partway through still leaves earlier chunks committed — [succeededCount] tracks
 * how many expenses were actually persisted before [failure] (if any) occurred, so callers can
 * warn the user about partial progress instead of silently losing track of it.
 */
data class BulkAddResult(
    val succeededCount: Int,
    val failure: Throwable?,
) {
    val isCompleteSuccess: Boolean get() = failure == null
}

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

    suspend fun addExpenses(expenses: List<Expense>): BulkAddResult
}
