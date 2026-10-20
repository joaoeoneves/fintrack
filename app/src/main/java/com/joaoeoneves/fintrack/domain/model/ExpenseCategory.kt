package com.joaoeoneves.fintrack.domain.model

/**
 * Expense categories. Persisted by [name], so the [kind] mapping can change without migrating
 * stored data.
 */
enum class ExpenseCategory(
    val kind: CategoryKind,
) {
    TRANSFER(CategoryKind.SPENDING),
    INVESTMENTS(CategoryKind.SAVING),
    SHOPPING(CategoryKind.SPENDING),
    RECURRING(CategoryKind.SPENDING),
}
