package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.amountMatchesQuery
import com.joaoeoneves.fintrack.ui.common.containsIgnoreCase

/**
 * Applies the type, category and search filters (all must match). The [category] only narrows
 * expenses: with [ActivityType.INCOME] it is ignored, and with [ActivityType.ALL] a selected
 * category hides income (income has no category). A blank [query] matches everything; [labels] are
 * the localized category names the query is also matched against.
 */
fun filterTransactions(
    list: List<Transaction>,
    type: ActivityType,
    category: CategoryId?,
    query: String,
    labels: Map<CategoryId, String>,
): List<Transaction> =
    list.filter { transaction ->
        transaction.matchesType(type) &&
            transaction.matchesCategory(type, category) &&
            (query.isBlank() || transaction.matchesActivityQuery(query, labels))
    }

/**
 * Sorts by date or by amount. Amount sorts use [sortAmountCents], so refunds sort below regular
 * expenses and income compares by its plain amount.
 */
fun sortTransactions(
    list: List<Transaction>,
    sort: SortOption,
): List<Transaction> =
    when (sort) {
        SortOption.DATE_DESC -> list.sortedByDescending { it.date }
        SortOption.DATE_ASC -> list.sortedBy { it.date }
        SortOption.AMOUNT_DESC -> list.sortedByDescending { it.sortAmountCents() }
        SortOption.AMOUNT_ASC -> list.sortedBy { it.sortAmountCents() }
    }

/** Date sorts group the list under day headers; amount sorts interleave days, so they stay flat. */
fun shouldGroupByDay(sort: SortOption): Boolean = sort == SortOption.DATE_DESC || sort == SortOption.DATE_ASC

/**
 * Whether this expense matches a search [query]: case-insensitively against its name, note,
 * category name (the user's own, or the displayed label from [labels]), a built-in category's id
 * (e.g. `SHOPPING`; generated ids of user categories are never matched), or as an amount (see
 * [amountMatchesQuery]).
 */
fun Expense.matchesQuery(
    query: String,
    labels: Map<CategoryId, String>,
): Boolean {
    val q = query.trim()
    return name.containsIgnoreCase(q) ||
        note.containsIgnoreCase(q) ||
        category.customName.containsIgnoreCase(q) ||
        labels[category.id].containsIgnoreCase(q) ||
        (category.builtIn && category.id.value.containsIgnoreCase(q)) ||
        amountMatchesQuery(amountCents, q)
}

/**
 * Whether this income entry matches a search [query]: case-insensitively against its source or
 * note, or as an amount (see [amountMatchesQuery]).
 */
fun Income.matchesQuery(query: String): Boolean {
    val q = query.trim()
    return source.containsIgnoreCase(q) || note.containsIgnoreCase(q) || amountMatchesQuery(amountCents, q)
}

private fun Transaction.matchesActivityQuery(
    query: String,
    labels: Map<CategoryId, String>,
): Boolean =
    when (this) {
        is Expense -> matchesQuery(query, labels)
        is Income -> matchesQuery(query)
    }

private fun Transaction.matchesType(type: ActivityType): Boolean =
    when (type) {
        ActivityType.ALL -> true
        ActivityType.EXPENSE -> this is Expense
        ActivityType.INCOME -> this is Income
    }

private fun Transaction.matchesCategory(
    type: ActivityType,
    category: CategoryId?,
): Boolean = category == null || type == ActivityType.INCOME || (this is Expense && this.category.id == category)

private fun Transaction.sortAmountCents(): Long =
    when (this) {
        is Expense -> signedAmountCents
        is Income -> amountCents
    }
