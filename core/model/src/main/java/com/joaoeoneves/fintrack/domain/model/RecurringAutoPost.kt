package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

/** Pending occurrences older than this many months are left for the user instead of being auto-posted. */
const val AUTO_POST_LOOKBACK_MONTHS = 12L

/**
 * The pending occurrences to confirm automatically on [today], oldest first (one due today counts). Empty
 * unless the item is active with [RecurringItem.autoPost] on, and also when the oldest pending one is more
 * than [AUTO_POST_LOOKBACK_MONTHS] months old: such a backlog stays manual in the upcoming list.
 */
fun RecurringItem.autoPostDueOccurrences(today: LocalDate): List<LocalDate> {
    val first = firstPendingOccurrence()
    val limit = endDate?.let { minOf(it, today) } ?: today
    val eligible = active && autoPost && first != null && first <= limit
    return if (eligible && first >= today.minusMonths(AUTO_POST_LOOKBACK_MONTHS)) {
        (lastIndexOnOrBefore(first)..lastIndexOnOrBefore(limit)).map { occurrence(it) }
    } else {
        emptyList()
    }
}
