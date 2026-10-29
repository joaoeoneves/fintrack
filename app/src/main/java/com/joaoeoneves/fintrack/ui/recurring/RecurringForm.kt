package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.MIN_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.paused
import com.joaoeoneves.fintrack.domain.model.resumed
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.LocalDate
import java.time.YearMonth

/**
 * The add/edit recurring form as typed: text fields stay strings until [validateRecurringForm]
 * accepts them and [toItem] converts them. [monthOfYear] is only used (and required) for yearly
 * items, [category] only for expenses.
 */
data class RecurringForm(
    val startDate: LocalDate,
    val type: RecurringType = RecurringType.EXPENSE,
    val name: String = "",
    val amountCents: Long = 0L,
    val category: Category? = null,
    val note: String = "",
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val dayText: String = "",
    val monthOfYear: Int? = null,
    val endDate: LocalDate? = null,
    val autoPost: Boolean = true,
    /** Paused: no occurrences are offered or posted until resumed (editing an existing item only). */
    val paused: Boolean = false,
)

/** Per-field validation messages; a `null` field is valid. */
data class RecurringFormErrors(
    val name: UiText? = null,
    val amount: UiText? = null,
    val category: UiText? = null,
    val day: UiText? = null,
    val month: UiText? = null,
    val endDate: UiText? = null,
    val note: UiText? = null,
) {
    val hasAny: Boolean
        get() = listOf(name, amount, category, day, month, endDate, note).any { it != null }
}

private fun message(resId: Int): UiText = UiText.StringResource(resId)

/**
 * Checks everything the Firestore rules check (so a signed-in write is never silently rejected):
 * a non-blank trimmed name of at most [MAX_NAME_LENGTH] code points, a positive amount, a known
 * category for expenses, a day of month in range, a month for yearly items, a note of at most
 * [MAX_NOTE_LENGTH] code points and an end date not before the start date. Returns `null` when valid.
 */
fun validateRecurringForm(form: RecurringForm): RecurringFormErrors? {
    val name = form.name.trim()
    val errors =
        RecurringFormErrors(
            name =
                when {
                    name.isEmpty() -> message(R.string.error_name_required)
                    name.codePointLength() > MAX_NAME_LENGTH -> message(R.string.error_recurring_too_long)
                    else -> null
                },
            amount = message(R.string.error_invalid_amount).takeIf { form.amountCents !in 1..MAX_AMOUNT_CENTS },
            category =
                message(R.string.error_category_required).takeIf {
                    form.type == RecurringType.EXPENSE && (form.category == null || form.category.isUnknown)
                },
            day =
                message(R.string.error_recurring_day_invalid).takeIf {
                    form.dayText.trim().toIntOrNull() !in dayRange
                },
            month =
                message(R.string.error_recurring_month_required).takeIf {
                    form.frequency == RecurringFrequency.YEARLY && form.monthOfYear !in monthRange
                },
            endDate =
                message(R.string.error_recurring_end_before_start).takeIf {
                    form.endDate != null && form.endDate < form.startDate
                },
            note =
                message(R.string.error_recurring_too_long).takeIf {
                    form.note.trim().codePointLength() > MAX_NOTE_LENGTH
                },
        )
    return errors.takeIf { it.hasAny }
}

private val dayRange = MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH
private val monthRange = MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR

/**
 * The item this (already validated) form describes: text trimmed, the category dropped for income,
 * the month dropped for monthly items. Keeps [existing]'s id, handled-through cursor, skipped occurrences,
 * pause day and active flag when editing; a new item is active with a blank id. [autoPost] starts on for a new item.
 */
fun RecurringForm.toItem(
    existing: RecurringItem?,
    today: LocalDate = LocalDate.now(),
): RecurringItem =
    toBaseItem(existing).let { item ->
        when {
            paused && item.active -> item.paused(today)
            !paused && !item.active && existing != null -> item.resumed(today)
            else -> item
        }
    }

private fun RecurringForm.toBaseItem(existing: RecurringItem?): RecurringItem =
    RecurringItem(
        id = existing?.id.orEmpty(),
        type = type,
        name = name.trim(),
        amountCents = amountCents,
        categoryId = category?.id.takeIf { type == RecurringType.EXPENSE },
        note = note.trim().ifBlank { null },
        frequency = frequency,
        dayOfMonth = dayText.trim().toInt(),
        monthOfYear = monthOfYear.takeIf { frequency == RecurringFrequency.YEARLY },
        startDate = startDate,
        endDate = endDate,
        handledThrough = existing?.handledThrough,
        active = existing?.active ?: true,
        autoPost = autoPost,
        skipped = existing?.skipped.orEmpty(),
        pausedAt = existing?.pausedAt,
    )

/** This item as an editable form; [category] is the item's resolved category (expenses only). */
fun RecurringItem.toForm(category: Category?): RecurringForm =
    RecurringForm(
        startDate = startDate,
        type = type,
        name = name,
        amountCents = amountCents,
        category = category,
        note = note.orEmpty(),
        frequency = frequency,
        dayText = dayOfMonth.toString(),
        monthOfYear = monthOfYear,
        endDate = endDate,
        autoPost = autoPost,
        paused = !active,
    )

/**
 * A blank form for a new item: starts and bills on [today]'s day (and month, for yearly items). The
 * category is left unset unless given, so the user has to choose one explicitly.
 */
fun newRecurringForm(
    today: LocalDate,
    category: Category? = null,
): RecurringForm =
    RecurringForm(
        startDate = today,
        category = category,
        dayText = today.dayOfMonth.toString(),
        monthOfYear = today.monthValue,
    )

/**
 * A new monthly expense form prefilled from a "make it recurring?" suggestion. It starts on [day] of the month after
 * [lastMonth] (clamped to that month's length), so auto-post does not add a duplicate of the month already paid.
 * The [category] is only set when it is still usable; otherwise the user has to choose one.
 */
fun prefilledRecurringForm(
    name: String,
    amountCents: Long,
    category: Category?,
    day: Int,
    lastMonth: YearMonth,
): RecurringForm {
    val month = lastMonth.plusMonths(1)
    val billingDay = day.coerceIn(MIN_DAY_OF_MONTH, MAX_DAY_OF_MONTH)
    val start = month.atDay(billingDay.coerceAtMost(month.lengthOfMonth()))
    return RecurringForm(
        startDate = start,
        name = name.take(MAX_NAME_LENGTH),
        amountCents = amountCents.takeIf { it in 1..MAX_AMOUNT_CENTS } ?: 0L,
        category = category,
        dayText = billingDay.toString(),
        monthOfYear = start.monthValue,
    )
}
