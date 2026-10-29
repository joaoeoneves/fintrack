package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.transactionFor
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Confirms or skips one occurrence of a recurring item. Both re-read the latest item and are a
 * no-op success when the occurrence was already handled (a double tap or a second device).
 */
class HandleRecurringOccurrence
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
        private val categoryRepository: CategoryRepository,
    ) {
        /**
         * Creates the expense/income for [occurrence] (with [amountOverrideCents] instead of the item's amount,
         * when set) and advances the item's cursor in one atomic
         * write. With [forReview] the transaction is flagged for the review inbox. Refused when [occurrence] is
         * after [today]; fails when the item no longer exists.
         */
        @Suppress("LongParameterList") // Optional override and review flag; callers pass them by name.
        suspend fun confirm(
            itemId: String,
            occurrence: LocalDate,
            today: LocalDate,
            zone: ZoneId,
            amountOverrideCents: Long? = null,
            forReview: Boolean = false,
        ): Result<Unit> =
            runNonCancelling {
                val item = latest(itemId)
                if (isHandled(item, occurrence)) return@runNonCancelling
                require(occurrence <= today) { "Occurrence $occurrence is in the future" }
                val catalog = categoryRepository.observeCategories().first()
                val transaction = item.transactionFor(occurrence, zone, catalog)
                val amounted =
                    when {
                        amountOverrideCents == null -> transaction
                        transaction is Expense -> transaction.copy(amountCents = amountOverrideCents)
                        transaction is Income -> transaction.copy(amountCents = amountOverrideCents)
                        else -> transaction
                    }
                val posted =
                    when {
                        !forReview -> amounted
                        amounted is Expense -> amounted.copy(needsReview = true)
                        amounted is Income -> amounted.copy(needsReview = true)
                        else -> amounted
                    }
                recurringRepository.confirmOccurrence(itemId, occurrence, posted).getOrThrow()
            }

        /** Records [occurrence] as skipped and moves the cursor to it, without creating a transaction. */
        suspend fun skip(
            itemId: String,
            occurrence: LocalDate,
        ): Result<Unit> =
            runNonCancelling {
                val item = latest(itemId)
                if (isHandled(item, occurrence) || occurrence in item.skipped) return@runNonCancelling
                recurringRepository.skipOccurrences(itemId, setOf(occurrence), advanceTo = occurrence).getOrThrow()
            }

        private suspend fun latest(itemId: String): RecurringItem =
            recurringRepository.observeRecurring().first().firstOrNull { it.id == itemId }
                ?: throw NoSuchElementException("Recurring item $itemId not found")

        private fun isHandled(
            item: RecurringItem,
            occurrence: LocalDate,
        ): Boolean = item.handledThrough?.let { it >= occurrence } == true
    }

/** Like [runCatching] but rethrows coroutine cancellation instead of capturing it. */
private inline fun runNonCancelling(block: () -> Unit): Result<Unit> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
