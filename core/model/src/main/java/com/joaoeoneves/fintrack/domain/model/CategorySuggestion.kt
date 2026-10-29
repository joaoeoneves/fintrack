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

/** A previously used expense name offered while typing, with the category it was last filed under. */
data class NameSuggestion(
    val name: String,
    val categoryId: CategoryId,
)

/** Up to [limit] earlier expense names matching [query] (prefix matches first, then frequency, then recency). */
@Suppress("UnusedParameter") // Body lands with the name-suggestion implementation.
fun nameSuggestions(
    expenses: List<Expense>,
    query: String,
    limit: Int = 3,
): List<NameSuggestion> = emptyList()
