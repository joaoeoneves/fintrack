package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income

/** Temporary stub: expenses only, with the type column. */
object TransactionCsvWriter {
    fun write(
        expenses: List<Expense>,
        incomes: List<Income>,
    ): String = ExpenseCsvWriter.write(expenses)
}
