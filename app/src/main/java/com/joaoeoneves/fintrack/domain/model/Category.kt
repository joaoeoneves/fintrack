package com.joaoeoneves.fintrack.domain.model

/** Stable identifier of a [Category]: a built-in name (e.g. `SHOPPING`) or a generated document id. */
@JvmInline
value class CategoryId(
    val value: String,
)

/**
 * An expense category: one of the [BuiltInCategories] (possibly overridden by the user) or a
 * user-defined one. [customName] is the user's own name (`null` for a built-in shown under its
 * localized default name); [iconKey]/[colorKey] are presentation keys resolved by the UI layer.
 * [archived] categories are hidden from pickers but still shown wherever they have data.
 * [groupId] is the id of the [CategoryGroup] the category is filed under, `null` when ungrouped (a
 * dangling id is treated the same way).
 */
data class Category(
    val id: CategoryId,
    val customName: String?,
    val iconKey: String,
    val colorKey: String,
    val kind: CategoryKind,
    val order: Int,
    val archived: Boolean,
    val builtIn: Boolean,
    val groupId: String? = null,
) {
    /** Whether this is a placeholder for an id that matches no known category (see [unknown]). */
    val isUnknown: Boolean get() = !builtIn && customName == null

    companion object {
        /**
         * Placeholder for an expense whose category id matches no known category (e.g. a deleted
         * user category): kept as spending, sorted last and hidden from pickers.
         */
        fun unknown(id: CategoryId): Category =
            Category(
                id = id,
                customName = null,
                iconKey = "unknown",
                colorKey = "slate",
                kind = CategoryKind.SPENDING,
                order = Int.MAX_VALUE,
                archived = true,
                builtIn = false,
            )
    }
}
