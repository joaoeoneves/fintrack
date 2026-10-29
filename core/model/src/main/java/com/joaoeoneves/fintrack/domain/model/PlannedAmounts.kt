package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Where a category's planned amount for a month comes from. */
enum class PlannedSource {
    /** An explicit budget limit. */
    BUDGET,

    /** The sum of the active expense recurring items falling in the month. */
    RECURRING,

    /** Nothing is planned. */
    NONE,
}

/** A category's planned amount for one month: [cents] is `null` exactly when [source] is [PlannedSource.NONE]. */
data class PlannedAmount(
    val cents: Long?,
    val source: PlannedSource,
)

private const val MONTHS_PER_YEAR = 12

/**
 * What is planned for a category in [month]. An explicit [budget] wins, unless [month] is one of its
 * skipped months; otherwise the amounts of the active EXPENSE items in [recurring] that have an occurrence
 * in the month are summed. [recurring] is the category's items (items of other kinds are ignored here).
 */
fun plannedForCategory(
    month: YearMonth,
    budget: Budget?,
    recurring: List<RecurringItem>,
): PlannedAmount {
    val active = budget?.asOf(month)
    if (active != null && month.monthValue !in active.skipMonths) {
        return PlannedAmount(active.limitCents, PlannedSource.BUDGET)
    }
    val due = recurring.filter { it.type == RecurringType.EXPENSE && it.active && it.occursIn(month) }
    return if (due.isEmpty()) {
        PlannedAmount(null, PlannedSource.NONE)
    } else {
        PlannedAmount(due.sumOf { it.amountCents }, PlannedSource.RECURRING)
    }
}

/** Whether this item has an occurrence in [month]: start/end dates respected, yearly only in its month. */
internal fun RecurringItem.occursIn(month: YearMonth): Boolean {
    if (!hasValidSchedule) return false
    val step = if (frequency == RecurringFrequency.YEARLY) MONTHS_PER_YEAR else 1
    val months = YearMonth.from(firstScheduled()).until(month, ChronoUnit.MONTHS)
    return months >= 0 &&
        months % step == 0L &&
        occurrence((months / step).toInt()).let { endDate == null || it <= endDate }
}
