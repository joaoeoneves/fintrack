package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** Aggregations against a catalog with user-defined, archived, overridden and unknown categories. */
class CategoryAggregationTest {
    private val date = Instant.parse("2026-10-10T12:00:00Z")
    private val gym = userCategory("gym", "Gym", order = 5)
    private val oldArchived = userCategory("old", "Old", order = 6, archived = true)
    private val catalog = mergeCategories(listOf(gym, oldArchived))

    // ---- cashFlowSummaryOf ----

    @Test
    fun cashFlow_builtInsOnly_hasOneZeroRowEach() {
        val summary = cashFlowSummaryOf(emptyList(), emptyList(), BuiltInCategories.all)

        val active = BuiltInCategories.all.filterNot { it.archived }
        assertEquals(active.map { CategoryTotal(it, 0L) }, summary.categoryTotals)
    }

    @Test
    fun cashFlow_archivedWithoutData_isExcluded_activeUserCategoryGetsZeroRow() {
        val summary = cashFlowSummaryOf(emptyList(), emptyList(), catalog)

        val ids = summary.categoryTotals.map { it.category.id.value }
        assertTrue("gym" in ids)
        assertTrue("old" !in ids)
    }

    @Test
    fun cashFlow_archivedWithData_isIncluded() {
        val summary = cashFlowSummaryOf(listOf(testExpense("e", 700L, date, oldArchived)), emptyList(), catalog)

        assertEquals(700L, summary.categoryTotals.single { it.category.id == oldArchived.id }.totalCents)
        assertEquals(700L, summary.spendingCents)
    }

    @Test
    fun cashFlow_unknownCategory_lastAndCountedAsSpending() {
        val ghost = Category.unknown(CategoryId("ghost"))

        val summary = cashFlowSummaryOf(listOf(testExpense("e", 900L, date, ghost)), emptyList(), catalog)

        assertEquals(ghost, summary.categoryTotals.last().category)
        assertEquals(900L, summary.categoryTotals.last().totalCents)
        assertEquals(900L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
    }

    @Test
    fun cashFlow_kindOverrideInCatalog_movesTotalsFromSpendingToSaved() {
        val foodAsSaving = BuiltInCategories.FOOD.copy(kind = CategoryKind.SAVING)
        val overridden = mergeCategories(listOf(foodAsSaving))
        // The expense carries a stale copy with the default (spending) kind.
        val expenses = listOf(testExpense("e", 1_000L, date, BuiltInCategories.FOOD))

        val summary = cashFlowSummaryOf(expenses, listOf(testIncome("i", 5_000L, date)), overridden)

        assertEquals(0L, summary.spendingCents)
        assertEquals(1_000L, summary.savedCents)
        assertEquals(0.2, summary.savingsRate!!, 1e-9)
    }

    @Test
    fun cashFlow_userSavingCategory_countsAsSaved() {
        val fund = userCategory("fund", "Fund", kind = CategoryKind.SAVING)

        val summary =
            cashFlowSummaryOf(listOf(testExpense("e", 2_000L, date, fund)), emptyList(), mergeCategories(listOf(fund)))

        assertEquals(2_000L, summary.savedCents)
        assertEquals(0L, summary.spendingCents)
    }

    // ---- budgetProgressOf ----

    @Test
    fun budgetProgress_archivedWithBudget_isShown_soItCanBeCleared() {
        val progress = budgetProgressOf(emptyList(), listOf(Budget(oldArchived.id, 5_000L)), catalog)

        assertEquals(5_000L, progress.single { it.category.id == oldArchived.id }.limitCents)
    }

    @Test
    fun budgetProgress_archivedWithoutBudgetOrSpend_isHidden() {
        val progress = budgetProgressOf(emptyList(), emptyList(), catalog)

        assertTrue(progress.none { it.category.id == oldArchived.id })
        assertEquals(catalog.active(), progress.map { it.category })
    }

    @Test
    fun budgetProgress_unknownWithSpend_isLast_andUnknownWithBudgetIsKept() {
        val ghost = Category.unknown(CategoryId("ghost"))
        val ghost2 = CategoryId("ghost2")

        val progress =
            budgetProgressOf(
                listOf(testExpense("e", 300L, date, ghost)),
                listOf(Budget(ghost2, 1_000L)),
                catalog,
            )

        assertEquals(listOf("ghost", "ghost2"), progress.takeLast(2).map { it.category.id.value })
        assertEquals(300L, progress.single { it.category.id == ghost.id }.spentCents)
        assertEquals(1_000L, progress.single { it.category.id == ghost2 }.limitCents)
        assertTrue(progress.last().category.isUnknown)
    }

    @Test
    fun budgetProgress_followsCatalogOrder() {
        val first = userCategory("first", order = -1)

        val progress = budgetProgressOf(emptyList(), emptyList(), mergeCategories(listOf(first)))

        assertEquals(
            "first",
            progress
                .first()
                .category.id.value,
        )
    }

    // ---- monthlyTotalsOf ----

    @Test
    fun monthlyTotals_usesCatalogForEachMonth() {
        val october = YearMonth.of(2026, 10)

        val totals =
            monthlyTotalsOf(
                listOf(testExpense("e", 400L, date, gym)),
                emptyList(),
                october..october,
                ZoneOffset.UTC,
                catalog,
            )

        assertEquals(
            400L,
            totals
                .single()
                .summary.categoryTotals
                .single { it.category.id == gym.id }
                .totalCents,
        )
    }

    @Test
    fun monthlyTotals_emptyRange_isEmpty_evenWithData() {
        val totals =
            monthlyTotalsOf(
                listOf(testExpense("e", 400L, date, gym)),
                emptyList(),
                YearMonth.of(2026, 11)..YearMonth.of(2026, 10),
                ZoneOffset.UTC,
                catalog,
            )

        assertTrue(totals.isEmpty())
    }
}
