package com.joaoeoneves.fintrack.domain.model

import java.text.Normalizer
import java.util.Locale

private val WHITESPACE = Regex("\\s+")
private val DIACRITICS = Regex("\\p{Mn}+")

/** [name] trimmed, with runs of whitespace collapsed, lowercased and stripped of diacritics. */
fun normalizedMerchantName(name: String): String =
    Normalizer
        .normalize(name.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")

/**
 * The category last used for each normalized expense name: the latest expense by date wins, ties
 * broken by the larger id. Expenses with a blank name are ignored.
 */
fun categorySuggestionIndex(expenses: List<Expense>): Map<String, CategoryId> =
    expenses
        .filter { it.name.isNotBlank() }
        .sortedWith(compareBy<Expense> { it.date }.thenBy { it.id })
        .associate { normalizedMerchantName(it.name) to it.category.id }
