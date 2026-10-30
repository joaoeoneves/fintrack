package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

/** Pending occurrences older than this many months are left for the user instead of being auto-posted. */
const val AUTO_POST_LOOKBACK_MONTHS = 12L

/**
 * The pending occurrences to confirm automatically on [today], oldest first (one due today counts). Empty
 * unless the item is active, and also when the oldest pending one is more
 * than [AUTO_POST_LOOKBACK_MONTHS] months old: such a backlog stays manual in the upcoming list.
 */
fun RecurringItem.autoPostDueOccurrences(today: LocalDate): List<LocalDate> {
    val first = firstPendingOccurrence()
    val limit = endDate?.let { minOf(it, today) } ?: today
    val eligible = active && first != null && first <= limit
    return if (eligible && first >= today.minusMonths(AUTO_POST_LOOKBACK_MONTHS)) {
        (lastIndexOnOrBefore(first)..lastIndexOnOrBefore(limit)).map { occurrence(it) }.filter { it !in skipped }
    } else {
        emptyList()
    }
}

/**
 * Past occurrences an auto-post expense item should already have a transaction for: those up to its cursor
 * ([RecurringItem.handledThrough]) within the lookback window, minus the periods the user skipped. Empty unless
 * the item is an active expense with a cursor. Never moves past [today] or the end date.
 */
fun RecurringItem.selfHealCandidates(today: LocalDate): List<LocalDate> {
    val cursor = handledThrough
    val eligible = active && type == RecurringType.EXPENSE
    if (!eligible || cursor == null) return emptyList()
    val from = maxOf(startDate, today.minusMonths(AUTO_POST_LOOKBACK_MONTHS))
    val skippedPeriods = skipped.map { periodKey(it) }.toSet()
    return occurrencesBetween(from, minOf(cursor, today)).filter { periodKey(it) !in skippedPeriods }
}

/**
 * The [candidates] with no transaction among [existingIds] (matched by item and period, so a transaction
 * kept after the billing day changed still counts).
 */
fun RecurringItem.missingOccurrences(
    candidates: List<LocalDate>,
    existingIds: Set<String>,
): List<LocalDate> {
    val present =
        existingIds
            .mapNotNull { parseOccurrenceId(it) }
            .filter { it.itemId == id }
            .map { periodKey(it.date) }
            .toSet()
    return candidates.filter { periodKey(it) !in present }
}

/**
 * This item with everything strictly before [today] marked handled (the cursor moves to its last occurrence before
 * [today], never backwards): an item that used to wait for manual confirmation does not post its backlog when it
 * becomes automatic. Today's occurrence stays pending and posts normally.
 */
fun RecurringItem.withoutBacklog(today: LocalDate): RecurringItem =
    copy(handledThrough = listOfNotNull(handledThrough, lastOccurrenceBefore(today)).maxOrNull())
