package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.occurrenceTransactionId
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.CancellationException
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
    ) {
        /** Deletes the expense that confirming [occurrence] created and rewinds the cursor. */
        suspend fun undoConfirm(
            itemId: String,
            occurrence: LocalDate,
            previousHandledThrough: LocalDate?,
        ): Result<Unit> =
            attempt {
                expenseRepository.deleteExpense(occurrenceTransactionId(itemId, occurrence)).getOrThrow()
                recurringRepository.setHandledThrough(itemId, previousHandledThrough).getOrThrow()
            }

        /** Removes [occurrence] from the skipped set and rewinds the cursor. */
        suspend fun undoSkip(
            itemId: String,
            occurrence: LocalDate,
            previousHandledThrough: LocalDate?,
        ): Result<Unit> =
            attempt {
                recurringRepository.unskipOccurrences(itemId, setOf(occurrence)).getOrThrow()
                recurringRepository.setHandledThrough(itemId, previousHandledThrough).getOrThrow()
            }

        private suspend fun attempt(block: suspend () -> Unit): Result<Unit> =
            runCatching { block() }.onFailure { if (it is CancellationException) throw it }
    }
