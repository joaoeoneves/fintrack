package com.joaoeoneves.fintrack.data.local

import android.util.Log
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_SKIPPED_OCCURRENCES
import com.joaoeoneves.fintrack.domain.model.MIN_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import kotlinx.serialization.Serializable
import java.time.DateTimeException
import java.time.LocalDate

private const val TAG = "RecurringRecords"

/**
 * A recurring bill/income template in the guest-mode store (and in backups). Enums are stored as
 * names and dates as ISO `yyyy-MM-dd` strings, like the Firestore document.
 */
@Serializable
data class LocalRecurringItem(
    val id: String,
    /** A [RecurringType] name. */
    val type: String,
    val name: String,
    val amountCents: Long,
    val categoryId: String? = null,
    val note: String? = null,
    /** A [RecurringFrequency] name. */
    val frequency: String,
    val dayOfMonth: Int,
    val monthOfYear: Int? = null,
    val startDate: String,
    val endDate: String? = null,
    val handledThrough: String? = null,
    val active: Boolean = true,
    /** Schema versions 11 to 22 only: read by the 22 -> 23 upgrade (false/absent = was manual), never written. */
    val autoPost: Boolean? = null,
    /** Added in schema version 17: ISO dates of the skipped occurrences; absent (empty) in older files. */
    val skipped: List<String> = emptyList(),
    /** Added in schema version 17: ISO date the item was paused; absent in older files. */
    val pausedAt: String? = null,
)

private fun parseLocalDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (e: DateTimeException) {
        Log.w(TAG, "Unparsable date '$raw'", e)
        null
    }

/** `Result.success(null)` for an absent date, a failure for an unparsable one. */
private fun parseOptionalDate(raw: String?): Result<LocalDate?> =
    if (raw == null) {
        Result.success(null)
    } else {
        parseLocalDate(raw)?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("Invalid date"))
    }

/** Maps to the domain item, or `null` (logged) for a bad enum, date, or day/month combination. */
internal fun LocalRecurringItem.toDomainOrNull(): RecurringItem? {
    val itemType = RecurringType.entries.find { it.name == type }
    val itemFrequency = RecurringFrequency.entries.find { it.name == frequency }
    val start = parseLocalDate(startDate)
    val end = parseOptionalDate(endDate)
    val handled = parseOptionalDate(handledThrough)
    val parsed =
        if (listOf(itemType, itemFrequency, start).any { it == null } || end.isFailure || handled.isFailure) {
            null
        } else {
            RecurringItem(
                id = id,
                type = checkNotNull(itemType),
                name = name,
                amountCents = amountCents,
                categoryId = categoryId?.let(::CategoryId),
                note = note,
                frequency = checkNotNull(itemFrequency),
                dayOfMonth = dayOfMonth,
                monthOfYear = monthOfYear,
                startDate = checkNotNull(start),
                endDate = end.getOrNull(),
                handledThrough = handled.getOrNull(),
                active = active,
                skipped = skipped.mapNotNull(::parseLocalDate).toSet(),
                pausedAt = pausedAt?.let(::parseLocalDate),
            )
        }
    return parsed?.takeIf { it.hasValidSchedule }.also {
        if (it == null) Log.w(TAG, "Dropping malformed recurring item $id")
    }
}

internal fun RecurringItem.toLocal(): LocalRecurringItem =
    LocalRecurringItem(
        id = id,
        type = type.name,
        name = name,
        amountCents = amountCents,
        categoryId = categoryId?.value,
        note = note,
        frequency = frequency.name,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        handledThrough = handledThrough?.toString(),
        active = active,
        skipped = skipped.sorted().map { it.toString() },
        pausedAt = pausedAt?.toString(),
    )

/** Mirror of the firestore.rules check (plus `endDate >= startDate`); throws [IllegalArgumentException]. */
internal fun validateRecurring(
    item: LocalRecurringItem,
    knownCategoryIds: Set<String>,
) {
    requireIdShape(item.id, "recurring")
    requireText(item.name, MAX_NAME_LENGTH, "Recurring name")
    require(item.amountCents in 1..MAX_AMOUNT_CENTS) { "Recurring amount must be 1..$MAX_AMOUNT_CENTS cents" }
    validateRecurringCategory(item, knownCategoryIds)
    requireValidNote(item.note)
    val frequency = RecurringFrequency.entries.find { it.name == item.frequency }
    requireNotNull(frequency) { "Invalid recurring frequency ${item.frequency}" }
    require(item.dayOfMonth in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH) { "Day of month must be 1..31" }
    require(
        if (frequency == RecurringFrequency.YEARLY) {
            item.monthOfYear in MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR
        } else {
            item.monthOfYear == null
        },
    ) { "Month of year is required for yearly items only (1..12)" }
    validateRecurringDates(item)
}

private fun validateRecurringCategory(
    item: LocalRecurringItem,
    knownCategoryIds: Set<String>,
) {
    when (item.type) {
        RecurringType.EXPENSE.name ->
            requireKnownCategory(requireNotNull(item.categoryId) { "An expense needs a category" }, knownCategoryIds)
        else -> throw IllegalArgumentException("Invalid recurring type ${item.type}")
    }
}

private fun validateRecurringDates(item: LocalRecurringItem) {
    val start = requireDate(item.startDate)
    val end = item.endDate?.let(::requireDate)
    item.handledThrough?.let(::requireDate)
    item.pausedAt?.let(::requireDate)
    require(item.skipped.size <= MAX_SKIPPED_OCCURRENCES) { "Too many skipped occurrences" }
    item.skipped.forEach(::requireDate)
    require(end == null || end >= start) { "End date is before start date" }
}

private fun requireDate(raw: String): LocalDate =
    try {
        LocalDate.parse(raw)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Invalid date", e)
    }
