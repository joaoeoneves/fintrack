package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.parseOccurrenceId
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Which bulk action a [BulkUndo] reverses. */
enum class BulkKind { CATEGORY, MEAL_CARD, DELETE }

/**
 * What it takes to reverse a bulk action: the [previous] state of the affected expenses and, for a delete, the
 * recurring occurrences it newly skipped (item id to dates), which the undo un-skips.
 */
data class BulkUndo(
    val kind: BulkKind,
    val previous: List<Expense>,
    val skipped: Map<String, Set<LocalDate>> = emptyMap(),
)

/**
 * Edits or deletes several expenses at once, each action with an [BulkUndo]. Bulk edits mark the expenses as reviewed.
 * Deleting a recurring occurrence also skips it (see [DeleteTransaction]), so auto-post does not re-create it.
 */
class BulkEditExpenses
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val recurringRepository: RecurringRepository,
    ) {
        suspend fun changeCategory(
            expenses: List<Expense>,
            category: Category,
        ): Result<BulkUndo> = update(BulkKind.CATEGORY, expenses) { it.copy(category = category) }

        suspend fun setMealCard(
            expenses: List<Expense>,
            enabled: Boolean,
        ): Result<BulkUndo> = update(BulkKind.MEAL_CARD, expenses) { it.copy(paidWithMealCard = enabled) }

        suspend fun delete(expenses: List<Expense>): Result<BulkUndo> {
            val skipped = skipOccurrencesOf(expenses)
            val deleted = expenseRepository.deleteExpenses(expenses.map { it.id })
            if (deleted.isFailure) {
                unskip(skipped)
                return Result.failure(deleted.exceptionOrNull() ?: IllegalStateException("Delete failed"))
            }
            return Result.success(BulkUndo(BulkKind.DELETE, expenses, skipped))
        }

        /** Reverses [undo]: restores the previous expenses, then un-skips the occurrences the delete skipped. */
        suspend fun undo(undo: BulkUndo): Result<Unit> {
            val restored =
                when (undo.kind) {
                    BulkKind.DELETE -> expenseRepository.addExpensesKeepingIds(undo.previous).map { }
                    BulkKind.CATEGORY, BulkKind.MEAL_CARD -> expenseRepository.updateExpenses(undo.previous)
                }
            if (restored.isSuccess) unskip(undo.skipped)
            return restored
        }

        private suspend fun update(
            kind: BulkKind,
            expenses: List<Expense>,
            change: (Expense) -> Expense,
        ): Result<BulkUndo> =
            expenseRepository
                .updateExpenses(expenses.map { change(it).copy(needsReview = false) })
                .map { BulkUndo(kind, expenses) }

        /** Skips the not-yet-skipped occurrences among [expenses]; returns the newly skipped dates per item. */
        private suspend fun skipOccurrencesOf(expenses: List<Expense>): Map<String, Set<LocalDate>> {
            val refs = expenses.mapNotNull { parseOccurrenceId(it.id) }
            if (refs.isEmpty()) return emptyMap()
            val items =
                runCatching { recurringRepository.observeRecurring().first() }
                    .onFailure { e -> if (e is CancellationException) throw e }
                    .getOrNull()
                    .orEmpty()
                    .filter { it.type == RecurringType.EXPENSE }
                    .associateBy { it.id }
            val newlySkipped = mutableMapOf<String, Set<LocalDate>>()
            refs.groupBy { it.itemId }.forEach { (itemId, itemRefs) ->
                val item = items[itemId] ?: return@forEach
                val dates = itemRefs.map { it.date }.filter { it !in item.skipped }.toSet()
                if (dates.isNotEmpty() &&
                    recurringRepository.skipOccurrences(itemId, dates, advanceTo = null).isSuccess
                ) {
                    newlySkipped[itemId] = dates
                }
            }
            return newlySkipped
        }

        private suspend fun unskip(skipped: Map<String, Set<LocalDate>>) {
            skipped.forEach { (itemId, dates) -> recurringRepository.unskipOccurrences(itemId, dates) }
        }
    }
