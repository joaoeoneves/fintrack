package com.joaoeoneves.fintrack.domain.model

/**
 * The built-in categories, defined in code. Expenses store a category by [Category.id], so these
 * ids must never change; the user may override their other fields with a same-id document.
 */
object BuiltInCategories {
    // Default display positions.
    private const val ORDER_TRANSFER = 0
    private const val ORDER_INVESTMENTS = 1
    private const val ORDER_SHOPPING = 2
    private const val ORDER_FOOD = 3
    private const val ORDER_RECURRING = 4

    val TRANSFER: Category = builtIn("TRANSFER", ORDER_TRANSFER, CategoryKind.SPENDING, "swap_horiz", "blue")
    val INVESTMENTS: Category = builtIn("INVESTMENTS", ORDER_INVESTMENTS, CategoryKind.SAVING, "trending_up", "emerald")
    val SHOPPING: Category = builtIn("SHOPPING", ORDER_SHOPPING, CategoryKind.SPENDING, "shopping_cart", "amber")
    val FOOD: Category = builtIn("FOOD", ORDER_FOOD, CategoryKind.SPENDING, "restaurant", "violet")

    /**
     * Retired: recurrence is a property of a transaction (see [isRecurringOccurrenceId]), not a
     * category. Kept resolvable (existing data still references it) but archived by default, so it
     * is hidden from pickers; a user override document may still unarchive it.
     */
    val RECURRING: Category =
        builtIn("RECURRING", ORDER_RECURRING, CategoryKind.SPENDING, "autorenew", "red").copy(archived = true)

    /** Every built-in, in their default display order. */
    val all: List<Category> = listOf(TRANSFER, INVESTMENTS, SHOPPING, FOOD, RECURRING)

    private val byIdMap: Map<CategoryId, Category> = all.associateBy { it.id }

    /** The built-in with this [id], or `null` if it is not a built-in id. */
    fun byId(id: CategoryId): Category? = byIdMap[id]

    fun isBuiltIn(id: CategoryId): Boolean = id in byIdMap

    private fun builtIn(
        id: String,
        order: Int,
        kind: CategoryKind,
        iconKey: String,
        colorKey: String,
    ): Category =
        Category(
            id = CategoryId(id),
            customName = null,
            iconKey = iconKey,
            colorKey = colorKey,
            kind = kind,
            order = order,
            archived = false,
            builtIn = true,
        )
}
