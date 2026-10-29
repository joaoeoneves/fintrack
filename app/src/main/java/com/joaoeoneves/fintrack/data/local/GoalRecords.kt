package com.joaoeoneves.fintrack.data.local

import android.util.Log
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_LINKED_RECURRING
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import java.time.DateTimeException
import java.time.LocalDate

private const val TAG = "GoalRecords"

/** Maps to the domain goal, or `null` (logged) for a bad enum or date. */
internal fun LocalSavingsGoal.toDomainOrNull(): SavingsGoal? {
    val type = GoalTargetType.entries.find { it.name == targetType }
    val start = parseGoalDate(startDate)
    val end = targetDate?.let(::parseGoalDate)
    val malformed = type == null || start == null || (targetDate != null && end == null)
    return if (malformed) {
        Log.w(TAG, "Dropping malformed goal $id")
        null
    } else {
        SavingsGoal(
            id = id,
            name = name,
            targetCents = targetCents,
            targetType = checkNotNull(type),
            startDate = checkNotNull(start),
            targetDate = end,
            initialCents = initialCents,
            linkedRecurringIds = linkedRecurringIds.toSet(),
            linkedCategoryId = linkedCategoryId?.let(::CategoryId),
            archived = archived,
            order = order,
            recurringId = recurringId,
        )
    }
}

internal fun SavingsGoal.toLocal(): LocalSavingsGoal =
    LocalSavingsGoal(
        id = id,
        name = name,
        targetCents = targetCents,
        targetType = targetType.name,
        startDate = startDate.toString(),
        targetDate = targetDate?.toString(),
        initialCents = initialCents,
        linkedRecurringIds = linkedRecurringIds.sorted(),
        linkedCategoryId = linkedCategoryId?.value,
        archived = archived,
        order = order,
        recurringId = recurringId,
    )

/** Mirror of the firestore.rules check (plus `targetDate >= startDate`); throws [IllegalArgumentException]. */
internal fun validateGoal(goal: LocalSavingsGoal) {
    requireIdShape(goal.id, "goal")
    requireText(goal.name, MAX_GOAL_NAME_LENGTH, "Goal name")
    require(goal.targetCents in 0..MAX_AMOUNT_CENTS) { "Goal target must be 0..$MAX_AMOUNT_CENTS cents" }
    require(goal.initialCents in 0..MAX_AMOUNT_CENTS) { "Goal initial amount must be 0..$MAX_AMOUNT_CENTS cents" }
    require(GoalTargetType.entries.any { it.name == goal.targetType }) { "Invalid goal type ${goal.targetType}" }
    val start = requireGoalDate(goal.startDate)
    val end = goal.targetDate?.let(::requireGoalDate)
    require(end == null || end >= start) { "Target date is before start date" }
    require(goal.linkedRecurringIds.size <= MAX_GOAL_LINKED_RECURRING) { "Too many linked recurring items" }
    goal.linkedCategoryId?.let(::requireCategoryIdShape)
    goal.recurringId?.let(::requireCategoryIdShape)
}

private fun parseGoalDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (e: DateTimeException) {
        Log.w(TAG, "Unparsable goal date '$raw'", e)
        null
    }

private fun requireGoalDate(raw: String): LocalDate =
    try {
        LocalDate.parse(raw)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Invalid date", e)
    }
