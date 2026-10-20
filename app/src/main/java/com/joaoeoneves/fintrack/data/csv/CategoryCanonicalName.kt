package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.ExpenseCategory

/**
 * Human-readable category name written to the CSV `category` column by [ExpenseCsvWriter] and
 * accepted (alongside the enum [ExpenseCategory.name]) by [ExpenseCsvParser].
 */
internal fun ExpenseCategory.canonicalName(): String =
    when (this) {
        ExpenseCategory.TRANSFER -> "Transfer"
        ExpenseCategory.INVESTMENTS -> "Investments"
        ExpenseCategory.SHOPPING -> "Shopping"
        ExpenseCategory.FOOD -> "Food"
        ExpenseCategory.RECURRING -> "Recurring"
    }
