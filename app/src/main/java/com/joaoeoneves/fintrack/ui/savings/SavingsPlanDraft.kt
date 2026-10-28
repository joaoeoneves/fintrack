package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MIN_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.isEnded
import java.time.LocalDate

/**
 * The editable state of a savings plan. Amounts are cents; [monthlyCents] 0 means no monthly amount and
 * [targetCents] 0 means no target. [dayText] is what the day field holds (digits only).
 */
data class SavingsPlanDraft(
    val name: String = "",
    val monthlyCents: Long = 0L,
    val dayText: String = DEFAULT_DAY.toString(),
    val categoryId: CategoryId? = BuiltInCategories.INVESTMENTS.id,
    val autoPost: Boolean = true,
    val targetCents: Long = 0L,
    val type: GoalTargetType = GoalTargetType.ANNUAL,
    val startDate: LocalDate = LocalDate.of(EPOCH_YEAR, 1, 1),
    val targetDate: LocalDate? = null,
    val initialCents: Long = 0L,
    val showErrors: Boolean = false,
) {
    val day: Int? get() = dayText.toIntOrNull()

    val hasMonthly: Boolean get() = monthlyCents > 0L

    val nameValid: Boolean get() = name.isNotBlank() && name.trim().codePointLength() <= MAX_GOAL_NAME_LENGTH

    val dayValid: Boolean get() = !hasMonthly || day in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH

    val categoryValid: Boolean get() = !hasMonthly || categoryId != null

    val datesValid: Boolean get() = type != GoalTargetType.TOTAL || targetDate == null || targetDate >= startDate

    val valid: Boolean get() = nameValid && dayValid && categoryValid && datesValid
}

internal const val DEFAULT_DAY = 1
private const val EPOCH_YEAR = 1970

/** The draft of [goal] (`null`: a new plan starting [today]) with the details of its owned monthly [item]. */
internal fun draftOf(
    goal: SavingsGoal?,
    item: RecurringItem?,
    today: LocalDate,
): SavingsPlanDraft {
    val active = item?.takeIf { it.active && !it.isEnded(today) }
    return SavingsPlanDraft(
        name = goal?.name.orEmpty(),
        monthlyCents = active?.monthlyCents() ?: 0L,
        dayText = (item?.dayOfMonth ?: DEFAULT_DAY).toString(),
        categoryId = item?.categoryId ?: BuiltInCategories.INVESTMENTS.id,
        autoPost = item?.autoPost ?: true,
        targetCents = goal?.targetCents ?: 0L,
        type = goal?.targetType ?: GoalTargetType.ANNUAL,
        startDate = goal?.startDate ?: today,
        targetDate = goal?.targetDate,
        initialCents = goal?.initialCents ?: 0L,
    )
}
