package ptech.joaoe.agenticusage.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import java.time.Instant

/**
 * Unit tests for [ExpenseCsvWriter], including a round-trip test through [ExpenseCsvParser].
 */
class ExpenseCsvWriterTest {
    private fun expense(
        id: String = "",
        name: String,
        amountCents: Long,
        category: ExpenseCategory,
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
                    category = ExpenseCategory.SHOPPING,
                    date = Instant.parse("2024-01-15T00:00:00Z"),
                    note = "with milk",
                ),
                expense(
                    name = "Rent",
                    amountCents = 150_000L,
                    category = ExpenseCategory.RECURRING,
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
                    category = ExpenseCategory.SHOPPING,
                    date = Instant.parse("2024-01-01T00:00:00Z"),
                    note = "she said \"hi\"",
                ),
            )

        val csv = ExpenseCsvWriter.write(expenses)
        val dataLine = csv.split("\n")[1]

        assertTrue(dataLine.contains("\"Lunch, team\""))
        assertTrue(dataLine.contains("\"she said \"\"hi\"\"\""))
    }

    // ---- round-trip ----

    @Test
    fun roundTrip_writeThenParse_reproducesOriginalExpenses_zeroFailures() {
        val originals =
            listOf(
                expense(
                    name = "Coffee",
                    amountCents = 599L, // not a round number of dollars
                    category = ExpenseCategory.SHOPPING,
                    date = Instant.parse("2024-01-15T08:30:00Z"),
                    note = null,
                ),
                expense(
                    name = "Team lunch, offsite",
                    amountCents = 12_345L,
                    category = ExpenseCategory.TRANSFER,
                    date = Instant.parse("2024-03-01T00:00:00Z"),
                    note = "with \"the whole\" team",
                ),
                expense(
                    name = "401k contribution",
                    amountCents = 500_000L,
                    category = ExpenseCategory.INVESTMENTS,
                    date = Instant.parse("2024-06-30T23:59:59Z"),
                    note = "",
                ),
                expense(
                    name = "Netflix",
                    amountCents = 1_599L,
                    category = ExpenseCategory.RECURRING,
                    date = Instant.parse("2024-12-31T00:00:00Z"),
                    note = "monthly, auto-renews",
                ),
            )

        val csv = ExpenseCsvWriter.write(originals)
        val outcome = ExpenseCsvParser.parse(csv)

        assertTrue(outcome is CsvImportOutcome.Parsed)
        val result = (outcome as CsvImportOutcome.Parsed).result

        assertTrue(result.failures.isEmpty())
        assertEquals(originals.size, result.validExpenses.size)

        originals.zip(result.validExpenses).forEach { (original, parsed) ->
            assertEquals(original.name, parsed.name)
            assertEquals(original.amountCents, parsed.amountCents)
            assertEquals(original.category, parsed.category)
            assertEquals(original.date, parsed.date)
            // "" and null both round-trip through CSV as a blank note -> null.
            assertEquals(original.note.orEmpty().ifBlank { null }, parsed.note)
            assertEquals("", parsed.id) // writer/parser don't round-trip id
        }
    }

    @Test
    fun roundTrip_allFourCategories_matchAfterWriteAndParse() {
        val originals =
            ExpenseCategory.entries.mapIndexed { index, category ->
                expense(
                    name = "Expense $index",
                    amountCents = (index + 1) * 111L,
                    category = category,
                    date = Instant.parse("2024-0${index + 1}-10T00:00:00Z"),
                    note = null,
                )
            }

        val csv = ExpenseCsvWriter.write(originals)
        val outcome = ExpenseCsvParser.parse(csv) as CsvImportOutcome.Parsed

        assertTrue(outcome.result.failures.isEmpty())
        assertEquals(originals.map { it.category }, outcome.result.validExpenses.map { it.category })
    }
}
