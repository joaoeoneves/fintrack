package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.occurrenceTransactionId
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Takes back a confirm or a skip done through [HandleRecurringOccurrence]: the occurrence is pending again and the
 * item's cursor returns to [previousHandledThrough] (what it was before the action).
 */
class UndoRecurringOccurrence
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
        private val expenseRepository: ExpenseRepository,
        // Only needed to undo an income item; Hilt always injects it.
        private val incomeRepository: IncomeRepository? = null,
    ) {
        /**
         * Deletes the transaction that confirming [occurrence] created (an expense or, for an [type] income item,
         * an income) and rewinds the cursor, see [rewind].
         */
        suspend fun undoConfirm(
            itemId: String,
            occurrence: LocalDate,
            previousHandledThrough: LocalDate?,
            type: RecurringType = RecurringType.EXPENSE,
        ): Result<Unit> =
            attempt {
                val id = occurrenceTransactionId(itemId, occurrence)
                when (type) {
                    RecurringType.EXPENSE -> expenseRepository.deleteExpense(id).getOrThrow()
                    RecurringType.INCOME ->
                        checkNotNull(incomeRepository) { "No income repository" }.deleteIncome(id).getOrThrow()
                }
                rewind(itemId, occurrence, previousHandledThrough)
            }

        /** Removes [occurrence] from the skipped set and rewinds the cursor. */
        suspend fun undoSkip(
            itemId: String,
            occurrence: LocalDate,
            previousHandledThrough: LocalDate?,
        ): Result<Unit> =
            attempt {
                recurringRepository.unskipOccurrences(itemId, setOf(occurrence)).getOrThrow()
                rewind(itemId, occurrence, previousHandledThrough)
            }

        // The cursor only goes back when it still is at this occurrence: a later action moved it on and must stay.
        private suspend fun rewind(
            itemId: String,
            occurrence: LocalDate,
            previousHandledThrough: LocalDate?,
        ) {
            val current = recurringRepository.observeRecurring().first().firstOrNull { it.id == itemId }
            if (current != null && current.handledThrough == occurrence) {
                recurringRepository.setHandledThrough(itemId, previousHandledThrough).getOrThrow()
            }
        }

        private suspend fun attempt(block: suspend () -> Unit): Result<Unit> =
            runCatching { block() }.onFailure { if (it is CancellationException) throw it }
    }
