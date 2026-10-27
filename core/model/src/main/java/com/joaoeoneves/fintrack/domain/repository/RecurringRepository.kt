package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RecurringRepository {
    /** The recurring items, sorted by start date then id. */
    fun observeRecurring(): Flow<List<RecurringItem>>

    /** Creates or replaces an item (a blank id creates one); returns the item's id. */
    suspend fun saveRecurring(item: RecurringItem): Result<String>

    /** Deletes an item (its generated transactions stay); a missing one is a no-op success. */
    suspend fun deleteRecurring(id: String): Result<Unit>

    /** Sets the confirmed/skipped cursor of an item; `null` clears it. */
    suspend fun setHandledThrough(
        id: String,
        date: LocalDate?,
    ): Result<Unit>

    /**
     * Points every recurring item of [source] at [target]; returns how many were changed. Awaited when
     * signed in.
     */
    suspend fun moveRecurringCategory(
        source: CategoryId,
        target: CategoryId,
    ): Result<Int>

    /**
     * Atomically stores [transaction] (the expense/income generated for [occurrence]) and moves the
     * item's cursor to [occurrence]; returns the transaction's id.
     */
    suspend fun confirmOccurrence(
        itemId: String,
        occurrence: LocalDate,
        transaction: Transaction,
    ): Result<String>
}
