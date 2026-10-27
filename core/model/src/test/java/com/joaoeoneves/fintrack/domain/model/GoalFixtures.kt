package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

/** A goal with neutral defaults (ANNUAL, 36 500 cents, starting 2026-01-01); every field overridable. */
@Suppress("LongParameterList") // Mirrors the model so each test overrides only what it cares about.
internal fun savingsGoal(
    id: String = "g1",
    name: String = "Goal $id",
    targetCents: Long = 36_500L,
    targetType: GoalTargetType = GoalTargetType.ANNUAL,
    startDate: LocalDate = LocalDate.of(2026, 1, 1),
    targetDate: LocalDate? = null,
    initialCents: Long = 0L,
    linkedRecurringIds: Set<String> = emptySet(),
    linkedCategoryId: CategoryId? = null,
    archived: Boolean = false,
    order: Int = 0,
) = SavingsGoal(
    id = id,
    name = name,
    targetCents = targetCents,
    targetType = targetType,
    startDate = startDate,
    targetDate = targetDate,
    initialCents = initialCents,
    linkedRecurringIds = linkedRecurringIds,
    linkedCategoryId = linkedCategoryId,
    archived = archived,
    order = order,
)
