package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Unit tests for [TransactionCsvWriter] (export only: the CSV import was removed in 5.5). */
class TransactionCsvWriterTest {
    private fun expense(
        name: String,
        amountCents: Long,
        category: Category,
        date: Instant,
        note: String?,
    ) = Expense(
        id = "",
        name = name,
        amountCents = amountCents,
        category = category,
        date = date,
        note = note,
    )

    private fun income(
        source: String,
        amountCents: Long,
        note: String? = null,
    ) = Income("i", source, amountCents, Instant.parse("2024-03-01T00:00:00Z"), note)

    @Test
    fun write_producesHeaderWithTypeColumnAndRows() {
        val expenses =
            listOf(
                expense("Coffee", 500L, BuiltInCategories.SHOPPING, Instant.parse("2024-01-15T00:00:00Z"), "with milk"),
                expense("Rent", 150_000L, BuiltInCategories.RECURRING, Instant.parse("2024-02-01T00:00:00Z"), null),
            )

        val lines = TransactionCsvWriter.write(expenses, emptyList()).split("\n")

        assertEquals("name,amountCents,category,date,note,type", lines[0])
        assertEquals("Coffee,500,Shopping,2024-01-15T00:00:00Z,with milk,expense", lines[1])
        assertEquals("Rent,150000,Recurring,2024-02-01T00:00:00Z,,expense", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun write_nothing_producesHeaderOnly() {
        assertEquals("name,amountCents,category,date,note,type", TransactionCsvWriter.write(emptyList(), emptyList()))
    }

    @Test
    fun write_incomeRows_arePositive_haveEmptyCategory_andComeAfterExpenses() {
        val exp = expense("Lunch", 900L, BuiltInCategories.SHOPPING, Instant.parse("2024-03-02T00:00:00Z"), null)

        val lines = TransactionCsvWriter.write(listOf(exp), listOf(income("Salary", 250_000L, "March"))).split("\n")

        assertEquals("Lunch,900,Shopping,2024-03-02T00:00:00Z,,expense", lines[1])
        assertEquals("Salary,250000,,2024-03-01T00:00:00Z,March,income", lines[2])
    }

    @Test
    fun write_refundExpense_isNegative_whileIncomeStaysPositive() {
        val refund =
            expense("Back", 300L, BuiltInCategories.SHOPPING, Instant.parse("2024-03-02T00:00:00Z"), null)
                .copy(isRefund = true)

        val lines = TransactionCsvWriter.write(listOf(refund), listOf(income("Gift", 1L))).split("\n")

        assertTrue(lines[1].startsWith("Back,-300,"))
        assertTrue(lines[2].startsWith("Gift,1,"))
    }

    @Test
    fun write_quotesFieldsContainingCommaQuoteOrNewline_inBothKinds() {
        val exp = expense("Lunch, team", 1L, BuiltInCategories.SHOPPING, Instant.EPOCH, "she said \"hi\"")

        val csv = TransactionCsvWriter.write(listOf(exp), listOf(income("Acme, Lda", 5L, "a\nb")))

        assertTrue(csv.contains("\"Lunch, team\""))
        assertTrue(csv.contains("\"she said \"\"hi\"\"\""))
        assertTrue(csv.contains("\"Acme, Lda\",5,,2024-03-01T00:00:00Z,\"a\nb\",income"))
    }
}
