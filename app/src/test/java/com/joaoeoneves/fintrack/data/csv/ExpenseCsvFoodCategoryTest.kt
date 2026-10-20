package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * CSV import/export of the [ExpenseCategory.FOOD] category: the shared canonical name "Food" is
 * what [ExpenseCsvWriter] writes and what [ExpenseCsvParser] accepts (case-insensitively, alongside
 * the enum name).
 */
class ExpenseCsvFoodCategoryTest {
    private val header = "name,amountCents,category,date,note"

    private val groceries =
        Expense(
            id = "",
            name = "Groceries",
            amountCents = 4_235L,
            category = ExpenseCategory.FOOD,
            date = Instant.parse("2026-10-05T18:30:00Z"),
            note = "weekly shop",
        )

    private fun parse(csv: String): CsvParseResult {
        val outcome = ExpenseCsvParser.parse(csv, ZoneOffset.UTC)
        check(outcome is CsvImportOutcome.Parsed) { "Expected Parsed but was $outcome" }
        return outcome.result
    }

    @Test
    fun canonicalName_ofFood_isFood() {
        assertEquals("Food", ExpenseCategory.FOOD.canonicalName())
    }

    @Test
    fun canonicalNames_arePairwiseDistinctIgnoringCase() {
        val names = ExpenseCategory.entries.map { it.canonicalName().lowercase() }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun parse_food_isCaseInsensitive_canonicalAndEnumForms() {
        val csv =
            """
            $header
            A,100,Food,2026-10-01,
            B,100,FOOD,2026-10-01,
            C,100,food,2026-10-01,
            D,100,fOoD,2026-10-01,
            """.trimIndent()

        val result = parse(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals(List(4) { ExpenseCategory.FOOD }, result.validExpenses.map { it.category })
    }

    @Test
    fun parse_foodLikeButWrongName_isRejected() {
        val csv =
            """
            $header
            A,100,Foods,2026-10-01,
            B,100,Groceries,2026-10-01,
            """.trimIndent()

        val result = parse(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(2, result.failures.size)
    }

    @Test
    fun write_food_outputsCanonicalFood() {
        val lines = ExpenseCsvWriter.write(listOf(groceries)).split("\n")

        assertEquals("Groceries,4235,Food,2026-10-05T18:30:00Z,weekly shop", lines[1])
    }

    @Test
    fun roundTrip_food_reproducesExpense() {
        val result = parse(ExpenseCsvWriter.write(listOf(groceries)))

        assertTrue(result.failures.isEmpty())
        assertEquals(listOf(groceries), result.validExpenses)
    }
}
