package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

@Suppress("TooManyFunctions") // One member per ExpenseRepository operation.
interface ExpenseRepository {
    fun observeExpenses(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Expense>>

    /**
     * Whether at least one expense exists. The default reads everything; real repositories override it
     * with a cheap single-document listener.
     */
    fun observeHasAnyExpense(): Flow<Boolean> {
        val all = observeExpenses(Instant.MIN, Instant.MAX)
        return all.map { it.isNotEmpty() }.distinctUntilChanged()
    }

    suspend fun addExpense(expense: Expense): Result<String>

    suspend fun updateExpense(expense: Expense): Result<Unit>

    suspend fun deleteExpense(id: String): Result<Unit>

    suspend fun getExpense(id: String): Result<Expense?>

    suspend fun getAllExpenses(): Result<List<Expense>>

    suspend fun addExpenses(expenses: List<Expense>): BulkAddResult

    /**
     * Whether any expense uses the category with [categoryId]. When signed in this needs the server
     * (a local cache can be incomplete), so it can fail while offline; as a guest it scans the
     * on-device data and does not need the network.
     */
    suspend fun hasExpenseWithCategory(categoryId: CategoryId): Result<Boolean>

    /** How many expenses use [categoryId]; same server/offline behaviour as [hasExpenseWithCategory]. */
    suspend fun countExpensesWithCategory(categoryId: CategoryId): Result<Int>

    /**
     * Re-categorizes every expense of [source] to [target]; returns how many were moved. Signed in
     * this is a series of awaited batches (needs the network); a failure partway leaves earlier
     * batches applied, and running it again finishes the rest.
     */
    suspend fun moveExpensesToCategory(
        source: CategoryId,
        target: CategoryId,
    ): Result<Int>
}
