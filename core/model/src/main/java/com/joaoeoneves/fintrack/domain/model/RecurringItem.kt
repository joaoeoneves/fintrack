package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

/** Whether a recurring item produces expenses or income. */
enum class RecurringType { EXPENSE, INCOME }

/** How often a recurring item repeats. */
enum class RecurringFrequency { MONTHLY, YEARLY }

/** Smallest and largest valid [RecurringItem.dayOfMonth]. */
const val MIN_DAY_OF_MONTH = 1
const val MAX_DAY_OF_MONTH = 31

/** Smallest and largest valid [RecurringItem.monthOfYear]. */
const val MIN_MONTH_OF_YEAR = 1
const val MAX_MONTH_OF_YEAR = 12

/**
 * A template for a bill or income that repeats (rent, a subscription, salary). The billing day is
 * explicit: [dayOfMonth] (clamped to the month's length, so 31 means "the last day" in short
 * months) and, for [RecurringFrequency.YEARLY], [monthOfYear]. [startDate] only says "counts from":
 * the first occurrence is the first scheduled date on or after it. Confirmed occurrences become
 * ordinary transactions; [handledThrough] is the latest occurrence confirmed or skipped. A blank
 * [id] means "not saved yet".
 *
 * [categoryId] is required for [RecurringType.EXPENSE] and `null` for [RecurringType.INCOME];
 * [monthOfYear] is non-null exactly when [frequency] is yearly.
 */
data class RecurringItem(
    val id: String,
    val type: RecurringType,
    val name: String,
    val amountCents: Long,
    val categoryId: CategoryId?,
    val note: String?,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int,
    val monthOfYear: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val handledThrough: LocalDate?,
    val active: Boolean,
) {
    /** Whether the day/month combination is within range and consistent with [frequency]. */
    val hasValidSchedule: Boolean
        get() =
            dayOfMonth in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH &&
                when (frequency) {
                    RecurringFrequency.MONTHLY -> monthOfYear == null
                    RecurringFrequency.YEARLY -> monthOfYear in MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR
                }
}
