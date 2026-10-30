package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income

private const val HEADER = "name,amountCents,category,date,note,type"
private const val TYPE_EXPENSE = "expense"
private const val TYPE_INCOME = "income"

/**
 * Writes expenses and income to one CSV text, with a trailing `type` column (`expense` or `income`). Expenses come
 * first, then income, each in the given order. Refunds are written with a negative `amountCents`
 * ([Expense.signedAmountCents]); income uses its source as the name, a positive amount and an empty category. A
 * reader of the older five-column format ignores the extra column, and one that knows it skips `income` rows.
 */
object TransactionCsvWriter {
    fun write(
        expenses: List<Expense>,
        incomes: List<Income>,
    ): String {
        val lines = mutableListOf(HEADER)
        expenses.forEach { expense ->
            lines +=
                row(
                    expense.name,
                    expense.signedAmountCents,
                    expense.category.canonicalName(),
                    expense.date.toString() to expense.note,
                    TYPE_EXPENSE,
                )
        }
        incomes.forEach { income ->
            lines += row(income.source, income.amountCents, "", income.date.toString() to income.note, TYPE_INCOME)
        }
        return lines.joinToString("\n")
    }

    private fun row(
        name: String,
        amountCents: Long,
        category: String,
        dateAndNote: Pair<String, String?>,
        type: String,
    ): String =
        listOf(
            escapeCsvField(name),
            amountCents.toString(),
            escapeCsvField(category),
            dateAndNote.first,
            escapeCsvField(dateAndNote.second.orEmpty()),
            type,
        ).joinToString(",")

    private fun escapeCsvField(field: String): String {
        val needsQuoting = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuoting) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}
