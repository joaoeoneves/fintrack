package com.joaoeoneves.fintrack.data.local

import kotlinx.serialization.Serializable

/** Version written into [LocalDataDocument.schemaVersion]; a file with a higher one is not read. */
internal const val LOCAL_SCHEMA_VERSION = 1

/**
 * The whole guest-mode data set, stored as one JSON file. Enums are stored as strings and dates as
 * ISO-8601 instants (lossless to the nanosecond). [revision] increases on every write.
 */
@Serializable
data class LocalDataDocument(
    val schemaVersion: Int = LOCAL_SCHEMA_VERSION,
    val revision: Long = 0,
    val expenses: List<LocalExpense> = emptyList(),
    val income: List<LocalIncome> = emptyList(),
    val budgets: List<LocalBudget> = emptyList(),
    /** Overrides of built-in categories plus user categories; `builtIn` is derived from the id. */
    val categories: List<LocalCategory> = emptyList(),
) {
    /** Whether there is no user data at all. */
    val isEmpty: Boolean get() = expenses.isEmpty() && income.isEmpty() && budgets.isEmpty() && categories.isEmpty()
}

@Serializable
data class LocalExpense(
    val id: String,
    val name: String,
    val amountCents: Long,
    val categoryId: String,
    /** ISO-8601 instant, e.g. `2026-01-31T10:15:30.123456789Z`. */
    val date: String,
    val note: String? = null,
    val isRefund: Boolean = false,
)

@Serializable
data class LocalIncome(
    val id: String,
    val source: String,
    val amountCents: Long,
    val date: String,
    val note: String? = null,
)

@Serializable
data class LocalBudget(
    val categoryId: String,
    val limitCents: Long,
)

@Serializable
data class LocalCategory(
    val id: String,
    val name: String? = null,
    val iconKey: String,
    val colorKey: String,
    /** A [com.joaoeoneves.fintrack.domain.model.CategoryKind] name. */
    val kind: String,
    val order: Int,
    val archived: Boolean = false,
)
