package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
     * Adds [dates] to the item's skipped occurrences and, when [advanceTo] is not `null`, moves the cursor
     * there in the same atomic write. Fails when the item does not exist. The default is a read-modify-write
     * through [saveRecurring]; real repositories override it with an atomic update.
     */
    suspend fun skipOccurrences(
        itemId: String,
        dates: Set<LocalDate>,
        advanceTo: LocalDate?,
    ): Result<Unit> =
        runCatching {
            val item =
                observeRecurring().first().firstOrNull { it.id == itemId }
                    ?: throw NoSuchElementException("Recurring item $itemId not found")
            saveRecurring(
                item.copy(skipped = item.skipped + dates, handledThrough = advanceTo ?: item.handledThrough),
            ).getOrThrow()
        }

    /** Removes [dates] from the item's skipped occurrences; fails when the item does not exist. */
    suspend fun unskipOccurrences(
        itemId: String,
        dates: Set<LocalDate>,
    ): Result<Unit> =
        runCatching {
            val item =
                observeRecurring().first().firstOrNull { it.id == itemId }
                    ?: throw NoSuchElementException("Recurring item $itemId not found")
            saveRecurring(item.copy(skipped = item.skipped - dates)).getOrThrow()
        }

    /**
     * One-time adoption of items created before 5.5, which waited for manual confirmation: every item whose
     * stored `autoPost` marker is not true gets its backlog marked handled (see `withoutBacklog`) and the marker
     * set, in one write. Returns how many items were adopted; a no-op for the local (guest) data, which upgrades
     * on load.
     */
    suspend fun adoptLegacyManualItems(today: LocalDate): Result<Int>

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
