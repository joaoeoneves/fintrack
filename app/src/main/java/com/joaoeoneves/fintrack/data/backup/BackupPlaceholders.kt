package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.DEFAULT_CATEGORY_ICON_KEY
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import com.joaoeoneves.fintrack.data.local.validateCategory
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind

internal const val PLACEHOLDER_CATEGORY_NAME = "Deleted category"
private const val PLACEHOLDER_COLOR_KEY = "slate"

/**
 * [categories] plus an archived placeholder for every category id that [expenses] or [budgets]
 * reference (recurring expenses included) but that is neither a built-in nor in [categories]
 * (e.g. a deleted user category), so a restore keeps those records instead of dropping them.
 * Placeholders are ordered after [maxOrder]; ids that could not pass category validation get none.
 */
internal fun withPlaceholderCategories(
    categories: List<LocalCategory>,
    expenses: List<LocalExpense>,
    budgets: List<LocalBudget>,
    maxOrder: Int,
    recurring: List<LocalRecurringItem> = emptyList(),
): List<LocalCategory> {
    val known = BuiltInCategories.all.mapTo(mutableSetOf()) { it.id.value } + categories.map { it.id }
    val referenced =
        expenses.map { it.categoryId } + budgets.map { it.categoryId } + recurring.mapNotNull { it.categoryId }
    val missing = referenced.distinct().filter { it !in known }
    val placeholders =
        missing
            .mapIndexed { index, id ->
                LocalCategory(
                    id = id,
                    name = PLACEHOLDER_CATEGORY_NAME,
                    iconKey = DEFAULT_CATEGORY_ICON_KEY,
                    colorKey = PLACEHOLDER_COLOR_KEY,
                    kind = CategoryKind.SPENDING.name,
                    order = maxOrder + 1 + index,
                    archived = true,
                )
            }.filter { runCatching { validateCategory(it) }.isSuccess }
    return categories + placeholders
}

/** This list with the names of the placeholders [withPlaceholderCategories] appended after [stored] replaced. */
internal fun List<LocalCategory>.namingPlaceholders(
    stored: Int,
    name: String,
): List<LocalCategory> = mapIndexed { index, category -> if (index >= stored) category.copy(name = name) else category }
