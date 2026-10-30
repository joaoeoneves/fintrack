package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Recurring bill/income templates kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalRecurringRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : RecurringRepository {
        override fun observeRecurring(): Flow<List<RecurringItem>> =
            store.data.map { doc ->
                doc.recurring.mapNotNull { it.toDomainOrNull() }.sortedWith(compareBy({ it.startDate }, { it.id }))
            }

        override suspend fun saveRecurring(item: RecurringItem): Result<String> =
            store.update { doc ->
                val record = item.toLocal().let { it.copy(id = it.id.ifBlank { idGenerator.newId() }) }
                validateRecurring(record, knownCategoryIds(doc))
                doc.copy(recurring = doc.recurring.upserted(record) { it.id == record.id }) to record.id
            }

        override suspend fun deleteRecurring(id: String): Result<Unit> =
            store.update { doc -> doc.copy(recurring = doc.recurring.filterNot { it.id == id }) to Unit }

        override suspend fun moveRecurringCategory(
            source: CategoryId,
            target: CategoryId,
        ): Result<Int> =
            store.update { doc ->
                require(target.value in knownCategoryIds(doc)) { "Unknown category ${target.value}" }
                val moved = doc.recurring.count { it.categoryId == source.value }
                val items =
                    doc.recurring.map { if (it.categoryId == source.value) it.copy(categoryId = target.value) else it }
                doc.copy(recurring = items) to moved
            }

        override suspend fun setHandledThrough(
            id: String,
            date: LocalDate?,
        ): Result<Unit> =
            store.update { doc ->
                doc.copy(recurring = doc.recurring.withCursor(id, date)) to Unit
            }

        // Nothing to adopt: the 22 -> 23 upgrade of the guest file (and of a restored backup) already did it.
        override suspend fun adoptLegacyManualItems(today: LocalDate): Result<Int> = Result.success(0)

        override suspend fun skipOccurrences(
            itemId: String,
            dates: Set<LocalDate>,
            advanceTo: LocalDate?,
        ): Result<Unit> =
            store.update { doc ->
                doc.copy(
                    recurring =
                        doc.recurring.withSkipped(itemId) { item ->
                            item.copy(
                                skipped = (item.skipped + dates.map { it.toString() }).distinct().sorted(),
                                handledThrough = advanceTo?.toString() ?: item.handledThrough,
                            )
                        },
                ) to Unit
            }

        override suspend fun unskipOccurrences(
            itemId: String,
            dates: Set<LocalDate>,
        ): Result<Unit> =
            store.update { doc ->
                doc.copy(
                    recurring =
                        doc.recurring.withSkipped(itemId) { item ->
                            item.copy(skipped = item.skipped - dates.map { it.toString() }.toSet())
                        },
                ) to Unit
            }

        override suspend fun confirmOccurrence(
            itemId: String,
            occurrence: LocalDate,
            transaction: Transaction,
        ): Result<String> =
            store.update { doc ->
                val known = knownCategoryIds(doc)
                val withTransaction =
                    when (transaction) {
                        is Expense -> {
                            val record = transaction.toLocal()
                            validateExpense(record, known)
                            doc.copy(expenses = doc.expenses.upserted(record) { it.id == record.id })
                        }
                        is Income -> {
                            val record = transaction.toLocal()
                            validateIncome(record)
                            doc.copy(income = doc.income.upserted(record) { it.id == record.id })
                        }
                    }
                withTransaction.copy(recurring = doc.recurring.withCursor(itemId, occurrence)) to transaction.id
            }

        private fun List<LocalRecurringItem>.withSkipped(
            id: String,
            change: (LocalRecurringItem) -> LocalRecurringItem,
        ): List<LocalRecurringItem> {
            require(any { it.id == id }) { "Recurring item $id not found" }
            return map { if (it.id == id) change(it) else it }
        }

        private fun List<LocalRecurringItem>.withCursor(
            id: String,
            date: LocalDate?,
        ): List<LocalRecurringItem> {
            require(any { it.id == id }) { "Recurring item $id not found" }
            return map { if (it.id == id) it.copy(handledThrough = date?.toString()) else it }
        }
    }
