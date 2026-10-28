package com.joaoeoneves.fintrack.ui.plan.goals

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_LINKED_RECURRING
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import java.time.LocalDate

/** The editable state of the sheet; amounts are cents, 0 meaning not typed. */
internal data class GoalDraft(
    val name: String,
    val targetCents: Long,
    val type: GoalTargetType,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    val initialCents: Long,
    val linkedRecurringIds: Set<String>,
    val linkedCategoryId: String?,
    val showErrors: Boolean = false,
) {
    /** Toggles a recurring item; a new link beyond [MAX_GOAL_LINKED_RECURRING] is ignored. */
    fun toggled(itemId: String): GoalDraft =
        when {
            itemId in linkedRecurringIds -> copy(linkedRecurringIds = linkedRecurringIds - itemId)
            linkedRecurringIds.size < MAX_GOAL_LINKED_RECURRING ->
                copy(linkedRecurringIds = linkedRecurringIds + itemId)
            else -> this
        }

    val nameValid: Boolean get() = name.isNotBlank()

    val datesValid: Boolean get() = targetDate == null || targetDate >= startDate

    val valid: Boolean get() = nameValid && targetCents > 0L && datesValid
}

internal fun draftOf(
    goal: SavingsGoal?,
    today: LocalDate,
): GoalDraft =
    GoalDraft(
        name = goal?.name.orEmpty(),
        targetCents = goal?.targetCents ?: 0L,
        type = goal?.targetType ?: GoalTargetType.ANNUAL,
        startDate = goal?.startDate ?: today,
        targetDate = goal?.targetDate,
        initialCents = goal?.initialCents ?: 0L,
        linkedRecurringIds = goal?.linkedRecurringIds.orEmpty(),
        linkedCategoryId = goal?.linkedCategoryId?.value,
    )

internal fun GoalDraft.toGoal(existing: SavingsGoal?): SavingsGoal {
    val total = type == GoalTargetType.TOTAL
    return SavingsGoal(
        id = existing?.id.orEmpty(),
        name = name,
        targetCents = targetCents,
        targetType = type,
        startDate = startDate,
        targetDate = targetDate.takeIf { total },
        initialCents = if (total) initialCents else 0L,
        linkedRecurringIds = linkedRecurringIds,
        linkedCategoryId = linkedCategoryId?.let(::CategoryId),
        archived = existing?.archived ?: false,
        order = existing?.order ?: 0,
    )
}
