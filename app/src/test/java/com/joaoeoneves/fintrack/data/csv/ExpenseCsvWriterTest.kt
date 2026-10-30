package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [ExpenseCsvWriter] (export only: the CSV import was removed in 5.5).
 */
class ExpenseCsvWriterTest {
    private fun expense(
        id: String = "",
        name: String,
        amountCents: Long,
        category: Category,
        date: Instant,
        note: String?,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    @Test
    fun write_producesExpectedHeaderAndRows() {
        val expenses =
            listOf(
                expense(
                    name = "Coffee",
                    amountCents = 500L,
                    category = BuiltInCategories.SHOPPING,
                    date = Instant.parse("2024-01-15T00:00:00Z"),
                    note = "with milk",
                ),
                expense(
                    name = "Rent",
                    amountCents = 150_000L,
                    category = BuiltInCategories.RECURRING,
                    date = Instant.parse("2024-02-01T00:00:00Z"),
                    note = null,
                ),
            )

        val csv = ExpenseCsvWriter.write(expenses)
        val lines = csv.split("\n")

        assertEquals("name,amountCents,category,date,note", lines[0])
        assertEquals("Coffee,500,Shopping,2024-01-15T00:00:00Z,with milk", lines[1])
        assertEquals("Rent,150000,Recurring,2024-02-01T00:00:00Z,", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun write_emptyList_producesHeaderOnly() {
        val csv = ExpenseCsvWriter.write(emptyList())

        assertEquals("name,amountCents,category,date,note", csv)
    }

    @Test
    fun write_quotesFieldsContainingCommaOrQuote() {
        val expenses =
            listOf(
                expense(
                    name = "Lunch, team",
                    amountCents = 1_234L,
                    category = BuiltInCategories.SHOPPING,
                    date = Instant.parse("2024-01-01T00:00:00Z"),
                    note = "she said \"hi\"",
                ),
            )

        val csv = ExpenseCsvWriter.write(expenses)
        val dataLine = csv.split("\n")[1]

        assertTrue(dataLine.contains("\"Lunch, team\""))
        assertTrue(dataLine.contains("\"she said \"\"hi\"\"\""))
    }

    @Test
    fun write_noteWithEmbeddedNewline_isQuotedAsOneField() {
        val csv =
            ExpenseCsvWriter.write(
                listOf(
                    expense(
                        name = "Team offsite",
                        amountCents = 45_000L,
                        category = BuiltInCategories.TRANSFER,
                        date = Instant.parse("2024-09-01T00:00:00Z"),
                        note = "Paid for everyone.\nWill be reimbursed.",
                    ),
                ),
            )

        assertTrue(csv.endsWith(",\"Paid for everyone.\nWill be reimbursed.\""))
    }
}
