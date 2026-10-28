package com.joaoeoneves.fintrack.domain.model

/**
 * One group of the plan's limits: its category [rows] with what they plan and spend. [group] is `null` for the
 * categories filed under no (or an unknown) group; [plannedCents] and [spentCents] sum the group's rows.
 */
data class LimitGroup(
    val group: CategoryGroup?,
    val plannedCents: Long,
    val spentCents: Long,
    val rows: List<CategoryBudget>,
)

/**
 * The [allocations] (every category that can carry a limit) filed under their group, groups in [groups]'s
 * [GroupProgress] order (an empty group still appears) and then "Other" when some category is ungrouped. The spent
 * amounts come from [progress] by category id; a category without spend keeps its own.
 */
fun limitGroupsOf(
    allocations: List<CategoryBudget>,
    progress: List<CategoryBudget>,
    groups: List<GroupProgress>,
): List<LimitGroup> {
    val spentById = progress.associate { it.category.id to it.spentCents }
    val rows = allocations.map { it.copy(spentCents = spentById[it.category.id] ?: it.spentCents) }
    val groupIds = groups.mapNotNullTo(mutableSetOf()) { it.group?.id }
    val byGroup = rows.groupBy { row -> row.category.groupId?.takeIf { it in groupIds } }
    val defined = groups.mapNotNull { it.group }.map { limitGroupOf(it, byGroup[it.id].orEmpty()) }
    val other = byGroup[null].orEmpty()
    return if (other.isEmpty()) defined else defined + limitGroupOf(null, other)
}

private fun limitGroupOf(
    group: CategoryGroup?,
    rows: List<CategoryBudget>,
): LimitGroup =
    LimitGroup(
        group = group,
        plannedCents = rows.sumOf { it.planned ?: 0L },
        spentCents = rows.sumOf { it.spentCents },
        rows = rows,
    )
