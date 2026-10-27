package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.mergeCategories
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** CSV export/import against a catalog with user-defined, overridden and archived categories. */
class ExpenseCsvCustomCategoryTest {
    private val header = "name,amountCents,category,date,note"
    private val gym = userCategory("k3Jd9aXq", "Gym", order = 5)
    private val oldArchived = userCategory("zz9Old", "Old Stuff", order = 6, archived = true)
    private val renamedFood = BuiltInCategories.FOOD.copy(customName = "Groceries")
    private val catalog = mergeCategories(listOf(gym, oldArchived, renamedFood))

    private fun expense(category: Category) =
        Expense(
            id = "",
            name = "x",
            amountCents = 100L,
            category = category,
            date = Instant.parse("2026-10-01T00:00:00Z"),
            note = null,
        )

    private fun parse(
        rows: String,
        categories: List<Category> = catalog,
    ): CsvParseResult {
        val outcome = ExpenseCsvParser.parse("$header\n$rows", ZoneOffset.UTC, categories)
        check(outcome is CsvImportOutcome.Parsed) { "Expected Parsed but was $outcome" }
        return outcome.result
    }

    // ---- writer ----

    @Test
    fun writer_customName_overriddenName_canonicalName_andRawIdForUnknown() {
        val csv =
            ExpenseCsvWriter.write(
                listOf(
                    expense(gym),
                    expense(renamedFood),
                    expense(BuiltInCategories.RECURRING),
                    expense(Category.unknown(CategoryId("gone123"))),
                ),
            )

        val categories = csv.split("\n").drop(1).map { it.split(",")[2] }
        assertEquals(listOf("Gym", "Groceries", "Recurring", "gone123"), categories)
    }

    // ---- parser ----

    @Test
    fun parser_matchesByCustomName_caseInsensitiveAndTrimmed() {
        val result = parse("A,100, gYm ,2026-10-01,\nB,100,GROCERIES,2026-10-01,")

        assertTrue(result.failures.isEmpty())
        assertEquals(listOf(gym.id, BuiltInCategories.FOOD.id), result.validExpenses.map { it.category.id })
        assertEquals(renamedFood, result.validExpenses[1].category)
    }

    @Test
    fun parser_matchesByIdIncludingArchived_andCanonicalAlias() {
        val rows =
            listOf(
                "A,100,K3JD9AXQ,2026-10-01,",
                "B,100,zz9old,2026-10-01,",
                "C,100,Food,2026-10-01,",
                "D,100,RECURRING,2026-10-01,",
            )
        val result = parse(rows.joinToString("\n"))

        assertTrue(result.failures.isEmpty())
        assertEquals(
            listOf(gym.id, oldArchived.id, BuiltInCategories.FOOD.id, BuiltInCategories.RECURRING.id),
            result.validExpenses.map { it.category.id },
        )
        assertTrue(result.validExpenses[1].category.archived)
    }

    @Test
    fun parser_archivedCategoryByName_isAccepted() {
        val result = parse("A,100,old stuff,2026-10-01,")

        assertEquals(oldArchived, result.validExpenses.single().category)
    }

    @Test
    fun parser_unknownName_rejectsRowWithExactReason() {
        val result = parse("A,100,Holiday,2026-10-01,")

        assertTrue(result.validExpenses.isEmpty())
        assertEquals("Unknown category: 'Holiday'", result.failures.single().reason)
    }

    @Test
    fun parser_defaultCatalogIsBuiltIns_customNameRejected() {
        val outcome = ExpenseCsvParser.parse("$header\nA,100,Gym,2026-10-01,", ZoneOffset.UTC)

        check(outcome is CsvImportOutcome.Parsed)
        assertEquals(
            "Unknown category: 'Gym'",
            outcome.result.failures
                .single()
                .reason,
        )
    }

    @Test
    fun roundTrip_withCustomCatalog_preservesCategories() {
        val original = listOf(gym, renamedFood, BuiltInCategories.INVESTMENTS, oldArchived).map { expense(it) }

        val outcome = ExpenseCsvParser.parse(ExpenseCsvWriter.write(original), ZoneOffset.UTC, catalog)

        check(outcome is CsvImportOutcome.Parsed)
        assertTrue(outcome.result.failures.isEmpty())
        assertEquals(original.map { it.category }, outcome.result.validExpenses.map { it.category })
    }
}
