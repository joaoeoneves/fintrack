package ptech.joaoe.agenticusage.data.csv

import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory

private const val HEADER = "name,amountCents,category,date,note"

/**
 * Writes [Expense] lists to CSV text in the format documented by the `seed-data` skill. The output
 * of [write] is guaranteed to be read back with zero failures by [ExpenseCsvParser.parse].
 */
object ExpenseCsvWriter {
    fun write(expenses: List<Expense>): String {
        val lines = mutableListOf(HEADER)
        expenses.forEach { expense ->
            lines +=
                listOf(
                    escapeCsvField(expense.name),
                    expense.amountCents.toString(),
                    escapeCsvField(expense.category.canonicalName()),
                    expense.date.toString(),
                    escapeCsvField(expense.note.orEmpty()),
                ).joinToString(",")
        }
        return lines.joinToString("\n")
    }

    private fun ExpenseCategory.canonicalName(): String =
        when (this) {
            ExpenseCategory.TRANSFER -> "Transfer"
            ExpenseCategory.INVESTMENTS -> "Investments"
            ExpenseCategory.SHOPPING -> "Shopping"
            ExpenseCategory.RECURRING -> "Recurring"
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
