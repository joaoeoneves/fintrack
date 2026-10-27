package com.joaoeoneves.fintrack.data.local

import kotlinx.serialization.Serializable

/** Version written into [LocalDataDocument.schemaVersion]; a file with a higher one is not read. */
internal const val LOCAL_SCHEMA_VERSION = 6

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
    /** Expected-income lines of the monthly plan (absent in schema v1 files). */
    val plannedIncome: List<LocalPlannedIncome> = emptyList(),
    /** Recurring bill/income templates (absent in schema v1 and v2 files). */
    val recurring: List<LocalRecurringItem> = emptyList(),
    /** Per-month plan settings (absent in schema v1 to v3 files; `daysOff` since v6). */
    val monthSettings: List<LocalMonthSettings> = emptyList(),
    /** Category groups (absent in schema v1 to v4 files). */
    val categoryGroups: List<LocalCategoryGroup> = emptyList(),
) {
    /** Whether there is no user data at all. */
    val isEmpty: Boolean get() =
        expenses.isEmpty() &&
            income.isEmpty() &&
            budgets.isEmpty() &&
            categories.isEmpty() &&
            plannedIncome.isEmpty() &&
            recurring.isEmpty() &&
            monthSettings.isEmpty() &&
            categoryGroups.isEmpty()
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
    /** Month numbers 1..12 the limit does not apply to (absent in schema v1 to v4 files). */
    val skipMonths: List<Int> = emptyList(),
)

@Serializable
data class LocalPlannedIncome(
    val id: String,
    val label: String,
    val amountCents: Long,
    val order: Int,
    /** An [com.joaoeoneves.fintrack.domain.model.IncomeBasis] name (absent in older files: FIXED). */
    val basis: String = "FIXED",
    /** An [com.joaoeoneves.fintrack.domain.model.IncomeFrequency] name (absent in older files: MONTHLY). */
    val frequency: String = "MONTHLY",
    val monthOfYear: Int? = null,
    /** ISO `yyyy-MM`, set for one-off lines. */
    val yearMonth: String? = null,
    val skipMonths: List<Int> = emptyList(),
)

/**
 * A month's manual working-days count and/or days off; [month] is ISO `yyyy-MM` and doubles as the record id.
 * [workingDays] is absent in a record that only holds days off, [daysOff] absent (0) in files before schema v6.
 */
@Serializable
data class LocalMonthSettings(
    val month: String,
    val workingDays: Int? = null,
    val daysOff: Int = 0,
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
    /** Id of the [LocalCategoryGroup] this category is filed under (absent in schema v1 to v4 files). */
    val groupId: String? = null,
)

/** A named bucket of categories; [order] is its position in the list. */
@Serializable
data class LocalCategoryGroup(
    val id: String,
    val name: String,
    val order: Int,
)
