package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.builtInEnglishNames
import com.joaoeoneves.fintrack.domain.model.effectiveName

/** Locale-independent English names of the built-in categories, as written to/read from CSV. */
internal val builtInCanonicalNames: Map<CategoryId, String> = builtInEnglishNames

/**
 * Category name written to the CSV `category` column by [ExpenseCsvWriter] and accepted by
 * [ExpenseCsvParser]: the user's own name, else the built-in's English name, else the raw id (an
 * unknown category placeholder).
 */
internal fun Category.canonicalName(): String = effectiveName()
