package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.model.withoutBacklog
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory [RecurringRepository] intended for tests. Items are exposed sorted by start date then id,
 * like the real implementations; a blank id on save gets a generated one. [confirmOccurrence] records
 * the transaction in [confirmedTransactions] and moves the item's cursor, as the real one does
 * atomically. Each `next*Error` fails exactly the next call of that kind.
 */
@Suppress("TooManyFunctions") // implements a wide repository interface
class FakeRecurringRepository(
    initialItems: List<RecurringItem> = emptyList(),
    var nextObserveError: Throwable? = null,
    var nextSaveError: Throwable? = null,
    var nextDeleteError: Throwable? = null,
    var nextSetHandledError: Throwable? = null,
    var nextConfirmError: Throwable? = null,
) : RecurringRepository {
    private val itemsFlow = MutableStateFlow(initialItems)

    var nextMoveCategoryError: Throwable? = null

    /** Fails the next [skipOccurrences] call. */
    var nextSkipError: Throwable? = null

    /** Fails the next [unskipOccurrences] call. */
    var nextUnskipError: Throwable? = null

    /** Names of the mutating calls made so far (`skip`, `unskip`), in order, for call-order assertions. */
    val calls: MutableList<String> = mutableListOf()
    /** When set, [adoptLegacyManualItems] returns it (failure included) without touching any item. */
    var adoptResult: Result<Int>? = null

    /** Ids of items that count as legacy manual ones: adoption moves their cursor with `withoutBacklog`. */
    val legacyManualIds: MutableSet<String> = mutableSetOf()

    /** The `today` of every [adoptLegacyManualItems] call, in order. */
    val adoptCalls: MutableList<LocalDate> = mutableListOf()
    private val confirmed = mutableListOf<Transaction>()
    private var nextId = 1

    /** Current items in insertion order (unsorted), for assertions. */
    val items: List<RecurringItem> get() = itemsFlow.value

    /** Transactions stored by [confirmOccurrence], in call order. */
    val confirmedTransactions: List<Transaction> get() = confirmed.toList()

    /** Replaces every item, e.g. to simulate a remote change. */
    fun setItems(items: List<RecurringItem>) {
        itemsFlow.value = items
    }

    override fun observeRecurring(): Flow<List<RecurringItem>> {
        nextObserveError?.let { error -> return flow { throw error } }
        return itemsFlow.map { list -> list.sortedWith(compareBy({ it.startDate }, { it.id })) }
    }

    override suspend fun saveRecurring(item: RecurringItem): Result<String> {
        nextSaveError?.let { error ->
            nextSaveError = null
            return Result.failure(error)
        }
        val saved = if (item.id.isBlank()) item.copy(id = "rec${nextId++}") else item
        val current = itemsFlow.value
        itemsFlow.value =
            if (current.any { it.id == saved.id }) {
                current.map { if (it.id == saved.id) saved else it }
            } else {
                current + saved
            }
        return Result.success(saved.id)
    }

    override suspend fun deleteRecurring(id: String): Result<Unit> {
        nextDeleteError?.let { error ->
            nextDeleteError = null
            return Result.failure(error)
        }
        itemsFlow.value = itemsFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun moveRecurringCategory(
        source: CategoryId,
        target: CategoryId,
    ): Result<Int> {
        nextMoveCategoryError?.let { error ->
            nextMoveCategoryError = null
            return Result.failure(error)
        }
        val current = itemsFlow.value
        itemsFlow.value = current.map { if (it.categoryId == source) it.copy(categoryId = target) else it }
        return Result.success(current.count { it.categoryId == source })
    }

    override suspend fun setHandledThrough(
        id: String,
        date: LocalDate?,
    ): Result<Unit> {
        nextSetHandledError?.let { error ->
            nextSetHandledError = null
            return Result.failure(error)
        }
        return withCursor(id, date).map { }
    }

    override suspend fun skipOccurrences(
        itemId: String,
        dates: Set<LocalDate>,
        advanceTo: LocalDate?,
    ): Result<Unit> {
        calls += "skip"
        nextSkipError?.let { error ->
            nextSkipError = null
            return Result.failure(error)
        }
        return update(itemId) {
            it.copy(skipped = it.skipped + dates, handledThrough = advanceTo ?: it.handledThrough)
        }
    }

    override suspend fun unskipOccurrences(
        itemId: String,
        dates: Set<LocalDate>,
    ): Result<Unit> {
        calls += "unskip"
        nextUnskipError?.let { error ->
            nextUnskipError = null
            return Result.failure(error)
        }
        return update(itemId) { it.copy(skipped = it.skipped - dates) }
    }

    override suspend fun adoptLegacyManualItems(today: LocalDate): Result<Int> {
        calls += "adopt"
        adoptCalls += today
        adoptResult?.let { return it }
        val adopted = itemsFlow.value.filter { it.id in legacyManualIds }
        itemsFlow.value = itemsFlow.value.map { if (it.id in legacyManualIds) it.withoutBacklog(today) else it }
        legacyManualIds.clear()
        return Result.success(adopted.size)
    }

    private fun update(
        id: String,
        change: (RecurringItem) -> RecurringItem,
    ): Result<Unit> {
        val current = itemsFlow.value
        if (current.none { it.id == id }) return Result.failure(NoSuchElementException("Recurring item $id not found"))
        itemsFlow.value = current.map { if (it.id == id) change(it) else it }
        return Result.success(Unit)
    }

    override suspend fun confirmOccurrence(
        itemId: String,
        occurrence: LocalDate,
        transaction: Transaction,
    ): Result<String> {
        nextConfirmError?.let { error ->
            nextConfirmError = null
            return Result.failure(error)
        }
        return withCursor(itemId, occurrence).map {
            confirmed += transaction
            transaction.id
        }
    }

    private fun withCursor(
        id: String,
        date: LocalDate?,
    ): Result<Unit> {
        val current = itemsFlow.value
        if (current.none { it.id == id }) return Result.failure(NoSuchElementException("Recurring item $id not found"))
        itemsFlow.value = current.map { if (it.id == id) it.copy(handledThrough = date) else it }
        return Result.success(Unit)
    }
}
