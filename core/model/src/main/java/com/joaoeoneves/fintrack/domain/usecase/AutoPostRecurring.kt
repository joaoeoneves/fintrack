package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.autoPostDueOccurrences
import com.joaoeoneves.fintrack.domain.model.occurrenceTransactionId
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Confirms the due occurrences of every active auto-post recurring item, oldest first. Safe to repeat: an
 * occurrence whose transaction already exists (a second device got there first) only advances the cursor.
 */
class AutoPostRecurring
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
        private val handleOccurrence: HandleRecurringOccurrence,
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
    ) {
        /** Returns how many transactions were created; an item stops at its first failure, the others go on. */
        suspend operator fun invoke(
            today: LocalDate,
            zone: ZoneId,
        ): Int {
            val items = recurringRepository.observeRecurring().first()
            return items.sumOf { item ->
                var posted = 0
                for (occurrence in item.autoPostDueOccurrences(today)) {
                    val id = occurrenceTransactionId(item.id, occurrence)
                    val exists =
                        when (item.type) {
                            RecurringType.EXPENSE -> expenseRepository.getExpense(id).getOrNull() != null
                            RecurringType.INCOME -> incomeRepository.getIncome(id).getOrNull() != null
                        }
                    val result =
                        if (exists) {
                            recurringRepository.setHandledThrough(item.id, occurrence)
                        } else {
                            handleOccurrence.confirm(item.id, occurrence, today, zone).onSuccess { posted++ }
                        }
                    if (result.isFailure) break
                }
                posted
            }
        }
    }
