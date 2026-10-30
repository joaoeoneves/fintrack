package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import java.time.LocalDate

/** A monthly expense item ("Rent", day 15, counting from 2026-01-01) with every field overridable. */
@Suppress("LongParameterList") // Mirrors the model so each test overrides only what it cares about.
fun recurringItem(
    id: String = "r1",
    type: RecurringType = RecurringType.EXPENSE,
    name: String = "Rent",
    amountCents: Long = 50_000L,
    categoryId: CategoryId? = if (type == RecurringType.EXPENSE) BuiltInCategories.FOOD.id else null,
    note: String? = null,
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    dayOfMonth: Int = 15,
    monthOfYear: Int? = if (frequency == RecurringFrequency.YEARLY) 3 else null,
    startDate: LocalDate = LocalDate.of(2026, 1, 1),
    endDate: LocalDate? = null,
    handledThrough: LocalDate? = null,
    active: Boolean = true,
    skipped: Set<LocalDate> = emptySet(),
    pausedAt: LocalDate? = null,
) = RecurringItem(
    id = id,
    type = type,
    name = name,
    amountCents = amountCents,
    categoryId = categoryId,
    note = note,
    frequency = frequency,
    dayOfMonth = dayOfMonth,
    monthOfYear = monthOfYear,
    startDate = startDate,
    endDate = endDate,
    handledThrough = handledThrough,
    active = active,
    skipped = skipped,
    pausedAt = pausedAt,
)

/** Shorthand for a date in tests. */
fun day(
    year: Int,
    month: Int,
    day: Int,
): LocalDate = LocalDate.of(year, month, day)
