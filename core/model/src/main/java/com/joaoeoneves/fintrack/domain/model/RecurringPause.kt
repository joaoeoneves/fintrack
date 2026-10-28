package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

/** The item paused as of [today]; remembers the day so [resumed] knows which occurrences the pause covered. */
fun RecurringItem.paused(today: LocalDate): RecurringItem = copy(active = false, pausedAt = today)

/**
 * The item resumed on [today]. Occurrences that fell inside the pause (from [RecurringItem.pausedAt], up to
 * yesterday) are recorded as skipped rather than coming back as overdue or being auto-posted; occurrences from
 * before the pause that were still pending stay pending. Without a recorded `pausedAt` (paused by an older
 * version) everything before today counts as handled. The cursor never moves backwards.
 */
fun RecurringItem.resumed(today: LocalDate): RecurringItem {
    val lastBefore = lastOccurrenceBefore(today)
    val windowStart = today.minusMonths(AUTO_POST_LOOKBACK_MONTHS)
    val pausedFrom = pausedAt
    val newCursor = listOfNotNull(handledThrough, lastBefore).maxOrNull()
    val firstPending = firstPendingOccurrence()
    val recorded: Set<LocalDate>
    val cursor: LocalDate?
    if (pausedFrom != null) {
        val gap =
            occurrencesBetween(pausedFrom, today.minusDays(1)).filter { date ->
                handledThrough?.let { date > it } != false
            }
        if (firstPending == null || firstPending >= pausedFrom) {
            cursor = newCursor
            recorded = gap.filter { it >= windowStart }.toSet()
        } else {
            cursor = handledThrough
            recorded = gap.toSet()
        }
    } else {
        cursor = newCursor
        recorded =
            if (cursor == null) {
                emptySet()
            } else {
                occurrencesBetween(handledThrough?.plusDays(1) ?: startDate, cursor)
                    .filter { it >= windowStart }
                    .toSet()
            }
    }
    return copy(active = true, pausedAt = null, handledThrough = cursor, skipped = skipped + recorded)
}
