package com.joaoeoneves.fintrack.domain.usecase

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
         * Creates the expense/income for [occurrence] and advances the item's cursor in one atomic
         * write. Refused when [occurrence] is after [today]; fails when the item no longer exists.
         */
        suspend fun confirm(
            itemId: String,
            occurrence: LocalDate,
            today: LocalDate,
            zone: ZoneId,
        ): Result<Unit> =
            runNonCancelling {
                val item = latest(itemId)
                if (isHandled(item, occurrence)) return@runNonCancelling
                require(occurrence <= today) { "Occurrence $occurrence is in the future" }
                val catalog = categoryRepository.observeCategories().first()
                recurringRepository
                    .confirmOccurrence(itemId, occurrence, item.transactionFor(occurrence, zone, catalog))
                    .getOrThrow()
            }

        /** Moves the cursor to [occurrence] without creating a transaction. */
        suspend fun skip(
            itemId: String,
            occurrence: LocalDate,
        ): Result<Unit> =
            runNonCancelling {
                val item = latest(itemId)
                if (isHandled(item, occurrence)) return@runNonCancelling
                recurringRepository.setHandledThrough(itemId, occurrence).getOrThrow()
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
