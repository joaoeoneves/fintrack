package com.joaoeoneves.fintrack.domain.model

/**
 * The starter grouping offered when there are no groups yet: each entry becomes a group (named by the UI in the
 * user's language) holding the listed built-in categories, in this order.
 */
enum class SuggestedGroup(
    val categoryIds: List<CategoryId>,
) {
    RECURRING(listOf(BuiltInCategories.RECURRING.id)),
    INVESTMENTS(listOf(BuiltInCategories.INVESTMENTS.id)),
    FOOD(listOf(BuiltInCategories.FOOD.id)),
    OTHER(listOf(BuiltInCategories.SHOPPING.id, BuiltInCategories.TRANSFER.id)),
}

/**
 * The suggested groups with the ids of their categories that exist in [categories] and aren't archived, in
 * [SuggestedGroup] order; a group left with no category is dropped.
 */
fun suggestedGroupsFor(categories: List<Category>): List<Pair<SuggestedGroup, List<CategoryId>>> {
    val activeIds = categories.filterNot { it.archived }.mapTo(HashSet()) { it.id }
    return SuggestedGroup.entries.mapNotNull { suggested ->
        val ids = suggested.categoryIds.filter { it in activeIds }
        if (ids.isEmpty()) null else suggested to ids
    }
}
