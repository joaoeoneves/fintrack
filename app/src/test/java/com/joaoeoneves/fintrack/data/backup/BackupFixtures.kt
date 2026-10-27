package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome

internal const val EXPORTED_AT = "2026-10-27T10:00:00Z"

internal fun backupExpense(
    id: String = "e1",
    categoryId: String = "FOOD",
    amountCents: Long = 1000,
    date: String = "2026-03-10T12:00:00Z",
    note: String? = null,
) = LocalExpense(id, "Lunch", amountCents, categoryId, date, note)

internal fun backupIncome(
    id: String = "i1",
    amountCents: Long = 5000,
    date: String = "2026-03-01T08:00:00Z",
) = LocalIncome(id, "Salary", amountCents, date)

internal fun backupCategory(
    id: String = "gym",
    name: String? = "Gym",
    order: Int = 10,
    archived: Boolean = false,
) = LocalCategory(id, name, "home", "teal", "SPENDING", order, archived)

internal fun backupDoc(
    expenses: List<LocalExpense> = emptyList(),
    income: List<LocalIncome> = emptyList(),
    budgets: List<LocalBudget> = emptyList(),
    categories: List<LocalCategory> = emptyList(),
    preferences: BackupPreferences? = null,
) = BackupDocument(
    exportedAt = EXPORTED_AT,
    preferences = preferences,
    expenses = expenses,
    income = income,
    budgets = budgets,
    categories = categories,
)

internal fun BackupDecodeResult.success(): BackupDecodeResult.Success =
    this as? BackupDecodeResult.Success ?: error("expected Success but was $this")
