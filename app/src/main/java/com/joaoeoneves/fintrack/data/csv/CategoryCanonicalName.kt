package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.effectiveName

/**
 * Category name written to the CSV `category` column by [ExpenseCsvWriter]: the user's own name, else the
 * built-in's English name, else the raw id (an unknown category placeholder).
 */
internal fun Category.canonicalName(): String = effectiveName()
