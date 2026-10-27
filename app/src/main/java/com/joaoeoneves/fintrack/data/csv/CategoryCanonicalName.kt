package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId

/** Locale-independent English names of the built-in categories, as written to/read from CSV. */
internal val builtInCanonicalNames: Map<CategoryId, String> =
    mapOf(
        BuiltInCategories.TRANSFER.id to "Transfer",
        BuiltInCategories.INVESTMENTS.id to "Investments",
        BuiltInCategories.SHOPPING.id to "Shopping",
        BuiltInCategories.FOOD.id to "Food",
        BuiltInCategories.RECURRING.id to "Recurring",
    )

/**
 * Category name written to the CSV `category` column by [ExpenseCsvWriter] and accepted by
 * [ExpenseCsvParser]: the user's own name, else the built-in's English name, else the raw id (an
 * unknown category placeholder).
 */
internal fun Category.canonicalName(): String = customName ?: builtInCanonicalNames[id] ?: id.value
