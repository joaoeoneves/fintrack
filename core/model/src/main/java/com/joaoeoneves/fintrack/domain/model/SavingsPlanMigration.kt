package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

private const val MAX_ID_LENGTH = 64

/**
 * The writes that move savings to savings plans: [newGoals] (one per standing order into a SAVING-kind category no
 * goal counts yet), [patchedGoals] (single-link goals that now own that item) and [savingEnvelopeIds] (envelopes
 * holding only SAVING-kind categories: saving is not part of the envelopes any more, so they are emptied, keeping
 * the budgets, and deleted). Empty once migrated.
 */
data class SavingsPlanMigration(
    val newGoals: List<SavingsGoal>,
    val patchedGoals: List<SavingsGoal>,
    val savingEnvelopeIds: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = newGoals.isEmpty() && patchedGoals.isEmpty() && savingEnvelopeIds.isEmpty()
}

/**
 * Computes the [SavingsPlanMigration]. Every active, not ended EXPENSE recurring item in a SAVING-kind category that
 * no goal counts becomes a goal `plan_<itemId>` (annual, no target) owning it; a goal without an own item whose links
 * hold exactly one such item that no other goal owns now owns it; multi-link goals are left alone. Items whose
 * category is not in [categories] yet (a partial cache) are skipped.
 */
fun savingsPlanMigrationOf(
    goals: List<SavingsGoal>,
    recurring: List<RecurringItem>,
    categories: List<Category>,
    today: LocalDate,
    envelopes: List<Envelope> = emptyList(),
): SavingsPlanMigration {
    val savingIds = categories.filter { it.kind == CategoryKind.SAVING }.mapTo(mutableSetOf()) { it.id }
    val savingItems = recurring.filter { it.type == RecurringType.EXPENSE && it.categoryId in savingIds }
    val byId = savingItems.associateBy { it.id }
    val owned = goals.mapNotNullTo(mutableSetOf()) { it.recurringId }
    val patched =
        goals
            .sortedWith(compareBy({ it.order }, { it.id }))
            .filter { it.recurringId == null }
            .mapNotNull { goal ->
                val only = goal.linkedRecurringIds.filter { it in byId }.singleOrNull()
                if (only == null || !owned.add(only)) null else goal.copy(recurringId = only)
            }
    val counted = goals.flatMapTo(mutableSetOf()) { it.countedRecurringIds } + owned
    val existingIds = goals.mapTo(mutableSetOf()) { it.id }
    var nextOrder = (goals.maxOfOrNull { it.order } ?: -1) + 1
    val created =
        savingItems
            .filter { it.active && !it.isEnded(today) && it.id !in counted }
            .sortedBy { it.id }
            .mapNotNull { item ->
                val id = "plan_${item.id}".take(MAX_ID_LENGTH)
                if (id in existingIds) return@mapNotNull null
                SavingsGoal(
                    id = id,
                    name = item.name.trim().take(MAX_GOAL_NAME_LENGTH),
                    targetCents = 0L,
                    targetType = GoalTargetType.ANNUAL,
                    startDate = item.startDate,
                    recurringId = item.id,
                    order = nextOrder++,
                )
            }
    return SavingsPlanMigration(created, patched, savingOnlyEnvelopeIds(envelopes, categories))
}

private fun savingOnlyEnvelopeIds(
    envelopes: List<Envelope>,
    categories: List<Category>,
): List<String> {
    val membersByEnvelope = categories.filter { it.groupId != null }.groupBy { it.groupId }
    return envelopes
        .filter { envelope ->
            val members = membersByEnvelope[envelope.id].orEmpty()
            members.isNotEmpty() && members.all { it.kind == CategoryKind.SAVING }
        }.sortedWith(compareBy({ it.order }, { it.id }))
        .map { it.id }
}
