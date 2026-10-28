package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val MONTHS_PER_YEAR = 12

/**
 * One row of the "upcoming" list: the oldest pending [date] of [item]. [overdueCount] is how many
 * further pending occurrences (after [date]) are already due; [isDue] is whether [date] is today
 * or earlier.
 */
data class UpcomingOccurrence(
    val item: RecurringItem,
    val date: LocalDate,
    val overdueCount: Int,
    val isDue: Boolean,
)

private val RecurringItem.stepMonths: Int
    get() = if (frequency == RecurringFrequency.YEARLY) MONTHS_PER_YEAR else 1

private fun RecurringItem.dateIn(month: YearMonth): LocalDate {
    val day = minOf(dayOfMonth, month.lengthOfMonth())
    return month.atDay(day)
}

/** The first scheduled date on or after [RecurringItem.startDate]. */
fun RecurringItem.firstScheduled(): LocalDate {
    val start = YearMonth.from(startDate)
    val sameYearMonth =
        if (frequency == RecurringFrequency.YEARLY) {
            YearMonth.of(startDate.year, checkNotNull(monthOfYear) { "Yearly item without month" })
        } else {
            start
        }
    val candidate = dateIn(sameYearMonth)
    return if (candidate >= startDate) candidate else dateIn(sameYearMonth.plusMonths(stepMonths.toLong()))
}

/** The [index]-th occurrence (0 is [firstScheduled]) with the day clamped to the month's length. */
fun RecurringItem.occurrence(index: Int): LocalDate {
    val month = YearMonth.from(firstScheduled()).plusMonths(index.toLong() * stepMonths)
    return dateIn(month)
}

/** Index of the last occurrence on or before [date], or -1 when none. Pure index arithmetic. */
internal fun RecurringItem.lastIndexOnOrBefore(date: LocalDate): Int {
    val months = YearMonth.from(firstScheduled()).until(YearMonth.from(date), ChronoUnit.MONTHS)
    val index = Math.floorDiv(months, stepMonths.toLong()).toInt()
    return when {
        index < 0 -> -1
        occurrence(index) > date -> index - 1
        else -> index
    }
}

/** The oldest occurrence after [RecurringItem.handledThrough] (or the first), `null` past the end date. */
fun RecurringItem.firstPendingOccurrence(): LocalDate? {
    val cursor = handledThrough
    val index = if (cursor == null) 0 else lastIndexOnOrBefore(cursor) + 1
    val date = occurrence(index)
    return date.takeUnless { endDate != null && it > endDate }
}

/**
 * How many pending occurrences beyond the oldest one are due on or before [today] (and the end
 * date); 0 when the oldest is not due yet or there is none.
 */
fun RecurringItem.overdueCount(today: LocalDate): Int {
    val first = firstPendingOccurrence()?.takeIf { it <= today } ?: return 0
    val limit = endDate?.let { minOf(it, today) } ?: today
    val firstIndex = lastIndexOnOrBefore(first)
    return (lastIndexOnOrBefore(limit) - firstIndex).coerceAtLeast(0)
}

/** The last occurrence strictly before [today] (and not after the end date), or `null`. */
fun RecurringItem.lastOccurrenceBefore(today: LocalDate): LocalDate? {
    val limit = today.minusDays(1).let { day -> endDate?.let { minOf(it, day) } ?: day }
    val index = lastIndexOnOrBefore(limit)
    return if (index < 0) null else occurrence(index)
}

/** Whether the end date has passed as of [today]. */
fun RecurringItem.isEnded(today: LocalDate): Boolean = endDate?.let { it < today } == true

/**
 * One row per active item that has a pending occurrence within [windowDays] of [today], sorted by
 * date then name. An item whose end date has passed still appears while occurrences on or before
 * the end date are unhandled (see [firstPendingOccurrence], which is `null` past the end date).
 */
fun upcomingOf(
    items: List<RecurringItem>,
    today: LocalDate,
    windowDays: Long,
): List<UpcomingOccurrence> {
    val horizon = today.plusDays(windowDays)
    return items
        .filter { it.active }
        .mapNotNull { item ->
            item.firstPendingOccurrence()?.takeIf { it <= horizon }?.let { date ->
                UpcomingOccurrence(item, date, item.overdueCount(today), isDue = date <= today)
            }
        }.sortedWith(compareBy({ it.date }, { it.item.name }))
}

/** Yearly cost (or income) of the active, not-ended items of [type]: monthly items count 12 times. */
fun annualizedCents(
    items: List<RecurringItem>,
    type: RecurringType,
    today: LocalDate,
): Long =
    items
        .filter { it.type == type && it.active && !it.isEnded(today) }
        .sumOf { it.amountCents * (if (it.frequency == RecurringFrequency.MONTHLY) MONTHS_PER_YEAR else 1) }
