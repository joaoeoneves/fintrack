package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import java.time.LocalDate

/** The stored plan a draft is saved over: the goal and its owned monthly item (either may be missing). */
internal class StoredPlan(
    val goal: SavingsGoal?,
    val item: RecurringItem?,
    val original: SavingsPlanDraft?,
    val nextOrder: Int,
)

/**
 * Writes a savings plan, which is a goal plus an optional hidden monthly recurring item. The item is always written
 * before the goal that points at it, and removed after it is created for a goal whose save failed, so a failure never
 * leaves a plan pointing at a missing item.
 */
internal class SavingsPlanWriter(
    private val goals: GoalRepository,
    private val recurring: RecurringRepository,
) {
    /** Saves [draft] over [stored]; returns the saved goal's id. */
    suspend fun save(
        draft: SavingsPlanDraft,
        stored: StoredPlan,
        today: LocalDate,
    ): Result<String> =
        runCatching {
            val name = draft.name.trim()
            var createdItemId: String? = null
            val recurringId =
                when {
                    draft.hasMonthly -> {
                        val id = recurring.saveRecurring(itemOf(draft, name, stored, today)).getOrThrow()
                        if (stored.item == null) createdItemId = id
                        id
                    }
                    // Cleared monthly amount: keep the item (and its posted history) but stop it.
                    stored.item != null -> {
                        if (stored.item.active) recurring.saveRecurring(stored.item.copy(active = false)).getOrThrow()
                        stored.item.id
                    }
                    else -> null
                }
            goals
                .saveGoal(goalOf(draft, name, stored, recurringId))
                .onFailure { createdItemId?.let { id -> recurring.deleteRecurring(id) } }
                .getOrThrow()
        }

    /** Deletes the plan's monthly item first, then the goal; restores the item if the goal cannot be deleted. */
    suspend fun delete(stored: StoredPlan): Result<Unit> =
        runCatching {
            val goal = requireNotNull(stored.goal)
            stored.item?.let { recurring.deleteRecurring(it.id).getOrThrow() }
            goals
                .deleteGoal(goal.id)
                .onFailure { stored.item?.let { item -> recurring.saveRecurring(item) } }
                .getOrThrow()
        }

    private fun goalOf(
        draft: SavingsPlanDraft,
        name: String,
        stored: StoredPlan,
        recurringId: String?,
    ): SavingsGoal {
        val existing = stored.goal
        val total = draft.type == GoalTargetType.TOTAL
        return SavingsGoal(
            id = existing?.id.orEmpty(),
            name = name,
            targetCents = draft.targetCents,
            targetType = draft.type,
            startDate = draft.startDate,
            targetDate = draft.targetDate.takeIf { total },
            initialCents = if (total) draft.initialCents else 0L,
            linkedRecurringIds = existing?.linkedRecurringIds.orEmpty(),
            linkedCategoryId = existing?.linkedCategoryId,
            archived = existing?.archived ?: false,
            order = existing?.order ?: stored.nextOrder,
            recurringId = recurringId,
        )
    }

    private fun itemOf(
        draft: SavingsPlanDraft,
        name: String,
        stored: StoredPlan,
        today: LocalDate,
    ): RecurringItem {
        val owned = stored.item ?: return newItemOf(draft, name, today)
        val revived = !owned.active || owned.isEnded(today)
        val base =
            if (revived) owned.copy(active = true, startDate = today, endDate = null) else owned
        // A yearly item the user did not touch keeps its schedule instead of becoming monthly.
        val untouched =
            owned.frequency == RecurringFrequency.YEARLY &&
                stored.original?.let { it.monthlyCents == draft.monthlyCents && it.day == draft.day } == true
        return base.copy(
            name = name,
            categoryId = draft.categoryId,
            autoPost = draft.autoPost,
            amountCents = if (untouched) owned.amountCents else draft.monthlyCents,
            dayOfMonth = if (untouched) owned.dayOfMonth else requireNotNull(draft.day),
            frequency = if (untouched) owned.frequency else RecurringFrequency.MONTHLY,
            monthOfYear = if (untouched) owned.monthOfYear else null,
        )
    }

    private fun newItemOf(
        draft: SavingsPlanDraft,
        name: String,
        today: LocalDate,
    ): RecurringItem =
        RecurringItem(
            id = "",
            type = RecurringType.EXPENSE,
            name = name,
            amountCents = draft.monthlyCents,
            categoryId = draft.categoryId,
            note = null,
            frequency = RecurringFrequency.MONTHLY,
            dayOfMonth = requireNotNull(draft.day),
            monthOfYear = null,
            startDate = today,
            endDate = null,
            handledThrough = null,
            active = true,
            autoPost = draft.autoPost,
        )
}
