package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.autoPostDueOccurrences
import com.joaoeoneves.fintrack.domain.model.missingOccurrences
import com.joaoeoneves.fintrack.domain.model.occurrenceIdPrefix
import com.joaoeoneves.fintrack.domain.model.occurrenceTransactionId
import com.joaoeoneves.fintrack.domain.model.selfHealCandidates
import com.joaoeoneves.fintrack.domain.model.transactionFor
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Confirms the due occurrences of every active auto-post recurring expense (income is a planned line, see
 * `RecordPayday`), oldest first. Safe to repeat: an
 * occurrence whose transaction already exists (a second device got there first) only advances the cursor.
 *
 * It also heals: an occurrence the item has already handled (before its cursor, within the lookback window, not
 * skipped) whose transaction is missing is created again under the same id. The check reads the server, so a
 * pending local write is not visible to it; callers order their writes so this is practically safe. Healing
 * never moves the cursor.
 */
class AutoPostRecurring
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
        private val handleOccurrence: HandleRecurringOccurrence,
        private val expenseRepository: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
    ) {
        /**
         * Returns how many transactions were created (healed ones included); an item stops at its first failure
         * when posting, the others go on.
         */
        suspend operator fun invoke(
            today: LocalDate,
            zone: ZoneId,
        ): Int = run(today, zone).count

        /** Like [invoke], but also says whether any item hit a failed read or write (retry later). */
        suspend fun run(
            today: LocalDate,
            zone: ZoneId,
        ): AutomationOutcome {
            val items = recurringRepository.observeRecurring().first()
            var catalog: List<Category>? = null
            var failed = false
            val count =
                items.filter { it.type == RecurringType.EXPENSE }.sumOf { item ->
                    val candidates = item.selfHealCandidates(today)
                    val healed =
                        if (candidates.isEmpty()) {
                            AutomationOutcome(0)
                        } else {
                            val categories =
                                catalog ?: categoryRepository.observeCategories().first().also { catalog = it }
                            heal(item, candidates, zone, categories)
                        }
                    val posted = post(item, today, zone)
                    failed = failed || healed.failed || posted.failed
                    healed.count + posted.count
                }
            return AutomationOutcome(count, failed)
        }

        private suspend fun heal(
            item: RecurringItem,
            candidates: List<LocalDate>,
            zone: ZoneId,
            catalog: List<Category>,
        ): AutomationOutcome {
            val prefix = occurrenceIdPrefix(item.id)
            val existing =
                expenseRepository
                    .expenseIdsBetween(
                        prefix + idDate(periodStart(item, candidates.first())),
                        prefix + idDate(periodEnd(item, candidates.last())),
                    ).getOrNull() ?: return AutomationOutcome(0, failed = true)
            var failed = false
            val created =
                item.missingOccurrences(candidates, existing).count { date ->
                    val expense = (item.transactionFor(date, zone, catalog) as Expense).copy(needsReview = true)
                    expenseRepository.addExpense(expense).isSuccess.also { if (!it) failed = true }
                }
            return AutomationOutcome(created, failed)
        }

        private suspend fun post(
            item: RecurringItem,
            today: LocalDate,
            zone: ZoneId,
        ): AutomationOutcome {
            var posted = 0
            var failed = false
            for (occurrence in item.autoPostDueOccurrences(today)) {
                val id = occurrenceTransactionId(item.id, occurrence)
                val exists = expenseRepository.getExpense(id).getOrNull() != null
                val result =
                    if (exists) {
                        recurringRepository.setHandledThrough(item.id, occurrence)
                    } else {
                        handleOccurrence
                            .confirm(item.id, occurrence, today, zone, forReview = true)
                            .onSuccess { posted++ }
                    }
                if (result.isFailure) {
                    failed = true
                    break
                }
            }
            return AutomationOutcome(posted, failed)
        }

        private fun idDate(date: LocalDate): String = DateTimeFormatter.BASIC_ISO_DATE.format(date)

        private fun periodStart(
            item: RecurringItem,
            date: LocalDate,
        ): LocalDate =
            if (item.frequency == RecurringFrequency.YEARLY) {
                Year.of(date.year).atDay(1)
            } else {
                YearMonth.from(date).atDay(1)
            }

        private fun periodEnd(
            item: RecurringItem,
            date: LocalDate,
        ): LocalDate =
            if (item.frequency ==
                RecurringFrequency.YEARLY
            ) {
                Year.of(date.year).atDay(date.lengthOfYear())
            } else {
                YearMonth.from(date).atEndOfMonth()
            }
    }
