package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import kotlinx.serialization.Serializable

/** Marker written into every backup file's `format` field. */
const val BACKUP_FORMAT = "fintrack-backup"

/** Version written into [BackupDocument.schemaVersion]; a file with a higher one is refused. */
const val BACKUP_SCHEMA_VERSION = 16

/**
 * A portable JSON backup of one data set (works from both guest and signed-in mode). The record
 * types are the same DTOs as the guest-mode store, so enums are strings and dates are ISO-8601
 * instants. [categories] holds user categories and overrides of built-ins only.
 */
@Serializable
data class BackupDocument(
    val format: String = BACKUP_FORMAT,
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    /** ISO-8601 instant the file was written. */
    val exportedAt: String,
    val appVersion: String? = null,
    val preferences: BackupPreferences? = null,
    val expenses: List<LocalExpense> = emptyList(),
    val income: List<LocalIncome> = emptyList(),
    val budgets: List<LocalBudget> = emptyList(),
    val categories: List<LocalCategory> = emptyList(),
    /** Added in schema version 2; absent (empty) in version 1 files. */
    val plannedIncome: List<LocalPlannedIncome> = emptyList(),
    /** Added in schema version 3; absent (empty) in older files. */
    val recurring: List<LocalRecurringItem> = emptyList(),
    /** Added in schema version 4 (`daysOff` in 6); absent (empty) in older files. */
    val monthSettings: List<LocalMonthSettings> = emptyList(),
    /** Added in schema version 5; absent (empty) in older files. */
    val categoryGroups: List<LocalCategoryGroup> = emptyList(),
    /** Added in schema version 8; absent (none) in older files. */
    val salaryProfile: LocalSalaryProfile? = null,
    /** Added in schema version 9; absent (empty) in older files. */
    val goals: List<LocalSavingsGoal> = emptyList(),
    /** Added in schema version 12; absent (none) in older files. */
    val automation: LocalAutomation? = null,
)

/** App preferences as enum names; an unrecognized name leaves the current value alone on restore. */
@Serializable
data class BackupPreferences(
    val theme: String? = null,
    val currency: String? = null,
    val language: String? = null,
)

/** How many records of each kind a backup holds. */
data class BackupCounts(
    val expenses: Int,
    val income: Int,
    val budgets: Int,
    val categories: Int,
    val plannedIncome: Int,
    val recurring: Int,
    val monthSettings: Int = 0,
    val categoryGroups: Int = 0,
    val salaryProfile: Int = 0,
    val goals: Int = 0,
    val automation: Int = 0,
) {
    val total: Int get() =
        expenses + income + budgets + categories + plannedIncome + recurring + monthSettings + categoryGroups +
            salaryProfile + goals + automation
}

fun BackupDocument.counts(): BackupCounts =
    BackupCounts(
        expenses = expenses.size,
        income = income.size,
        budgets = budgets.size,
        categories = categories.size,
        plannedIncome = plannedIncome.size,
        recurring = recurring.size,
        monthSettings = monthSettings.size,
        categoryGroups = categoryGroups.size,
        salaryProfile = if (salaryProfile != null) 1 else 0,
        goals = goals.size,
        automation = if (automation != null) 1 else 0,
    )
