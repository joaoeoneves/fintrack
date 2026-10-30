package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Expense

private const val HEADER = "name,amountCents,category,date,note"

/**
 * Writes [Expense] lists to CSV text in the format documented by the `seed-data` skill. Refunds
 * are written with a negative `amountCents` ([Expense.signedAmountCents]).
 */
object ExpenseCsvWriter {
    fun write(expenses: List<Expense>): String {
        val lines = mutableListOf(HEADER)
        expenses.forEach { expense ->
            lines +=
                listOf(
                    escapeCsvField(expense.name),
                    expense.signedAmountCents.toString(),
                    escapeCsvField(expense.category.canonicalName()),
                    expense.date.toString(),
                    escapeCsvField(expense.note.orEmpty()),
                ).joinToString(",")
        }
        return lines.joinToString("\n")
    }

    private fun escapeCsvField(field: String): String {
        val needsQuoting = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuoting) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}
