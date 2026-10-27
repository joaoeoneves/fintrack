package com.joaoeoneves.fintrack.ui.plan.goals

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_LINKED_RECURRING
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText
import java.time.LocalDate

/** The editable state of the sheet; amounts stay text until saved. */
internal data class GoalDraft(
    val name: String,
    val targetText: String,
    val type: GoalTargetType,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    val initialText: String,
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

    val targetCents: Long? get() = parseAmountCents(targetText).getOrNull()

    /** The initial amount: nothing typed is 0, a non-amount is `null`. */
    val initialCents: Long? get() = if (initialText.isBlank()) 0L else parseAmountCents(initialText).getOrNull()

    val nameValid: Boolean get() = name.isNotBlank()

    val datesValid: Boolean get() = targetDate == null || targetDate >= startDate

    val valid: Boolean get() = nameValid && targetCents != null && initialCents != null && datesValid
}

internal fun draftOf(
    goal: SavingsGoal?,
    decimalSeparator: Char,
    today: LocalDate,
): GoalDraft =
    GoalDraft(
        name = goal?.name.orEmpty(),
        targetText = goal?.targetCents?.toAmountInputText(decimalSeparator).orEmpty(),
        type = goal?.targetType ?: GoalTargetType.ANNUAL,
        startDate = goal?.startDate ?: today,
        targetDate = goal?.targetDate,
        initialText =
            goal
                ?.initialCents
                ?.takeIf { it > 0 }
                ?.toAmountInputText(decimalSeparator)
                .orEmpty(),
        linkedRecurringIds = goal?.linkedRecurringIds.orEmpty(),
        linkedCategoryId = goal?.linkedCategoryId?.value,
    )

internal fun GoalDraft.toGoal(existing: SavingsGoal?): SavingsGoal {
    val total = type == GoalTargetType.TOTAL
    return SavingsGoal(
        id = existing?.id.orEmpty(),
        name = name,
        targetCents = checkNotNull(targetCents),
        targetType = type,
        startDate = startDate,
        targetDate = targetDate.takeIf { total },
        initialCents = if (total) checkNotNull(initialCents) else 0L,
        linkedRecurringIds = linkedRecurringIds,
        linkedCategoryId = linkedCategoryId?.let(::CategoryId),
        archived = existing?.archived ?: false,
        order = existing?.order ?: 0,
    )
}
