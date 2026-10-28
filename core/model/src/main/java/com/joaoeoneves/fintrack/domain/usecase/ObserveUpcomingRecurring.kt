package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.model.annualizedCents
import com.joaoeoneves.fintrack.domain.model.upcomingOf
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** Days ahead of today that still count as "upcoming". */
const val UPCOMING_WINDOW_DAYS = 30L

/** What the dashboard card shows: the upcoming rows plus the annualized totals of active items. */
data class RecurringOverview(
    val upcoming: List<UpcomingOccurrence>,
    val annualExpenseCents: Long,
    val hasItems: Boolean,
)

/** Observes the [RecurringOverview] as of a given day. */
class ObserveUpcomingRecurring
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
    ) {
        operator fun invoke(today: LocalDate): Flow<RecurringOverview> =
            recurringRepository.observeRecurring().map { items ->
                RecurringOverview(
                    upcoming = upcomingOf(items, today, UPCOMING_WINDOW_DAYS),
                    annualExpenseCents = annualizedCents(items, RecurringType.EXPENSE, today),
                    hasItems = items.isNotEmpty(),
                )
            }
    }
