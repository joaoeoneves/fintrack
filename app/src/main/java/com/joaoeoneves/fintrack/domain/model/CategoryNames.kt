package com.joaoeoneves.fintrack.domain.model

/** Locale-independent English names of the built-in categories. */
val builtInEnglishNames: Map<CategoryId, String> =
    mapOf(
        BuiltInCategories.TRANSFER.id to "Transfer",
        BuiltInCategories.INVESTMENTS.id to "Investments",
        BuiltInCategories.SHOPPING.id to "Shopping",
        BuiltInCategories.FOOD.id to "Food",
        BuiltInCategories.RECURRING.id to "Recurring",
    )

/** The English default name of the built-in [id], or `null` for any other id. */
fun canonicalNameOf(id: CategoryId): String? = builtInEnglishNames[id]

/**
 * Locale-independent name: the user's own name, else the built-in's English name, else the raw id
 * (an unknown category placeholder).
 */
fun Category.effectiveName(): String = customName ?: canonicalNameOf(id) ?: id.value
