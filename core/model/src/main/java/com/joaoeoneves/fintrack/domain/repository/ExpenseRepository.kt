package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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

    /**
     * The ids of the expenses whose id lies in the inclusive range [firstId]..[lastId] (lexicographic). Signed
     * in this reads the server (a partial cache could report an existing expense as missing), so it can fail
     * while offline. The default filters [getAllExpenses]; real repositories override it.
     */
    suspend fun expenseIdsBetween(
        firstId: String,
        lastId: String,
    ): Result<Set<String>> = getAllExpenses().map { all -> all.map { it.id }.filter { it in firstId..lastId }.toSet() }

    /** The expenses flagged [Expense.needsReview], across all months. */
    fun observeNeedingReview(): Flow<List<Expense>> = flowOf(emptyList())

    /** Clears [Expense.needsReview] on the expenses with these [ids] (missing ids are ignored). */
    suspend fun markReviewed(ids: Collection<String>): Result<Unit> = Result.success(Unit)

    suspend fun addExpense(expense: Expense): Result<String>

    suspend fun updateExpense(expense: Expense): Result<Unit>

    suspend fun deleteExpense(id: String): Result<Unit>

    /**
     * Adds these expenses under the ids they carry (a blank id gets a generated one) and returns the ids in order.
     * The default adds them one by one; real repositories write them in one go.
     */
    suspend fun addExpensesKeepingIds(expenses: List<Expense>): Result<List<String>> =
        runCatching { expenses.map { addExpense(it).getOrThrow() } }

    /** Updates these existing expenses. The default updates them one by one; real repositories write in one go. */
    suspend fun updateExpenses(expenses: List<Expense>): Result<Unit> =
        runCatching {
            expenses.forEach { updateExpense(it).getOrThrow() }
        }

    /** Deletes the expenses with these [ids], one by one by default; real repositories write in one go. */
    suspend fun deleteExpenses(ids: Collection<String>): Result<Unit> =
        runCatching {
            ids.forEach { deleteExpense(it).getOrThrow() }
        }

    /** The refunds (across all months) that link to an original expense through [Expense.refundOf]. */
    fun observeLinkedRefunds(): Flow<List<Expense>> = flowOf(emptyList())

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
