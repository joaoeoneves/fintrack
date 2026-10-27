package com.joaoeoneves.fintrack.domain.model

/**
 * Merges stored category documents with the [BuiltInCategories]: a document with a built-in id
 * replaces that built-in's fields (staying [Category.builtIn]), any other document is appended.
 * Sorted by [Category.order], then id; archived categories are included.
 */
fun mergeCategories(docs: List<Category>): List<Category> {
    val docsById = docs.associateBy { it.id }
    val builtIns = BuiltInCategories.all.map { builtIn -> docsById[builtIn.id]?.copy(builtIn = true) ?: builtIn }
    val custom = docs.filterNot { BuiltInCategories.isBuiltIn(it.id) }.distinctBy { it.id }
    return (builtIns + custom).sortedWith(compareBy<Category> { it.order }.thenBy { it.id.value })
}

/** The catalog entry for [id], else the built-in default, else a [Category.unknown] placeholder. */
fun List<Category>.resolve(id: CategoryId): Category {
    val stored = find { it.id == id }
    return stored ?: BuiltInCategories.byId(id) ?: Category.unknown(id)
}

/** The non-archived categories, in catalog order. */
fun List<Category>.active(): List<Category> = filterNot { it.archived }

/**
 * The categories a picker offers: the active ones, plus [current] (appended) when it is archived or
 * unknown, so an existing expense's category stays selectable.
 */
fun List<Category>.optionsIncluding(current: Category?): List<Category> {
    val options = active()
    return if (current == null || options.any { it.id == current.id }) options else options + current
}

/**
 * Category for a new expense: Shopping while active, else the first active one, else Shopping. The
 * retired [BuiltInCategories.RECURRING] is never picked, even if the user unarchived it.
 */
fun defaultCategory(categories: List<Category>): Category {
    val active = categories.active().filterNot { it.id == BuiltInCategories.RECURRING.id }
    return active.find { it.id == BuiltInCategories.SHOPPING.id }
        ?: active.firstOrNull()
        ?: BuiltInCategories.SHOPPING
}

/** These expenses with each category replaced by its current [catalog] entry (see [resolve]). */
fun List<Expense>.resolvedWith(catalog: List<Category>): List<Expense> =
    map { expense ->
        val resolved = catalog.resolve(expense.category.id)
        if (resolved == expense.category) expense else expense.copy(category = resolved)
    }

/**
 * Rows for a per-category breakdown: every active category plus any category in [presentIds]
 * (e.g. archived ones with data), in catalog order, with ids missing from the catalog last.
 */
fun List<Category>.rowsFor(presentIds: Set<CategoryId>): List<Category> {
    val known = filter { !it.archived || it.id in presentIds }
    val knownIds = known.mapTo(mutableSetOf()) { it.id }
    val unknown =
        presentIds
            .filterNot { it in knownIds }
            .map { resolve(it) }
            .sortedWith(compareBy<Category> { it.order }.thenBy { it.id.value })
    return known + unknown
}
