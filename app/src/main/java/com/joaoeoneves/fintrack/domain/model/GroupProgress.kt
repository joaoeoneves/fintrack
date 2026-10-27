package com.joaoeoneves.fintrack.domain.model

/**
 * One group's planned vs actual for a month: [group] is `null` for the implicit "Other" group of categories
 * filed under no (or an unknown) group. [categories] are the group's rows that have something planned or spent.
 */
data class GroupProgress(
    val group: CategoryGroup?,
    val planned: Long,
    val spent: Long,
    val categories: List<CategoryBudget>,
) {
    val isOver: Boolean get() = planned > 0 && spent > planned

    /** Whether part of [planned] comes from recurring items rather than a budget. */
    val hasRecurringPlanned: Boolean get() = categories.any { it.plannedSource == PlannedSource.RECURRING }
}

/**
 * Sums the budget [rows] per group: planned and spent over each group's categories (archived ones with spend
 * included), the groups in [groups] order (an empty group still appears) and then the implicit "Other" group
 * (`group == null`), shown only when some category with data is ungrouped. A category's group is read from the
 * [categories] catalog; an id matching no group counts as ungrouped.
 */
fun groupProgressOf(
    rows: List<CategoryBudget>,
    categories: List<Category>,
    groups: List<CategoryGroup>,
): List<GroupProgress> {
    val groupIds = groups.mapTo(mutableSetOf()) { it.id }
    val used = rows.filter { it.planned != null || it.spentCents > 0 }
    val byGroup = used.groupBy { row -> categories.resolve(row.category.id).groupId?.takeIf { it in groupIds } }
    val sorted = groups.sortedWith(compareBy({ it.order }, { it.id }))
    val progress = sorted.map { progressOf(it, byGroup[it.id].orEmpty()) }
    val other = byGroup[null].orEmpty()
    return if (other.isEmpty()) progress else progress + progressOf(null, other)
}

private fun progressOf(
    group: CategoryGroup?,
    rows: List<CategoryBudget>,
): GroupProgress =
    GroupProgress(
        group = group,
        planned = rows.sumOf { it.planned ?: 0L },
        spent = rows.sumOf { it.spentCents },
        categories = rows,
    )
