package com.joaoeoneves.fintrack.ui.expense.list

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Pure-function tests for [matchesQuery] and [toListContent] on expenses. */
class ExpenseListFilterTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private val coffee =
        Expense(
            id = "c",
            name = "Morning Coffee",
            amountCents = 450L,
            category = ExpenseCategory.SHOPPING,
            date = now,
            note = "oat milk",
        )
    private val rent =
        coffee.copy(id = "r", name = "Rent", amountCents = 90_000L, category = ExpenseCategory.RECURRING, note = null)
    private val etf =
        coffee.copy(id = "i", name = "ETF", amountCents = 20_000L, category = ExpenseCategory.INVESTMENTS, note = null)
    private val groceries =
        coffee.copy(id = "f", name = "Supermarket", amountCents = 3_210L, category = ExpenseCategory.FOOD, note = null)
    private val ptLabels =
        mapOf(
            ExpenseCategory.SHOPPING to "Compras",
            ExpenseCategory.FOOD to "Alimentação",
            ExpenseCategory.RECURRING to "Recorrente",
        )

    private fun List<Expense>.content(
        query: String = "",
        category: ExpenseCategory? = null,
        labels: Map<ExpenseCategory, String> = emptyMap(),
        sort: SortOption = SortOption.DATE_DESC,
    ) = toListContent(Period.CurrentMonth, false, ExpenseListFilter(query, category, labels), sort)

    // ---- matchesQuery ----

    @Test
    fun matchesQuery_name_caseInsensitive() {
        assertTrue(coffee.matchesQuery("COFFEE", emptyMap()))
    }

    @Test
    fun matchesQuery_note() {
        assertTrue(coffee.matchesQuery("OAT", emptyMap()))
        assertFalse(rent.matchesQuery("oat", emptyMap()))
    }

    @Test
    fun matchesQuery_localizedCategoryLabel() {
        assertTrue(coffee.matchesQuery("compr", ptLabels))
        assertFalse(rent.matchesQuery("compr", ptLabels))
    }

    @Test
    fun matchesQuery_ptFoodLabel_matchesFoodExpenseOnly() {
        assertTrue(groceries.matchesQuery("Alimentação", ptLabels))
        assertTrue(groceries.matchesQuery("aliment", ptLabels))
        assertFalse(coffee.matchesQuery("Alimentação", ptLabels))
        assertFalse(rent.matchesQuery("Alimentação", ptLabels))
    }

    @Test
    fun matchesQuery_foodEnumName_withNoLabels() {
        assertTrue(groceries.matchesQuery("food", emptyMap()))
        assertFalse(coffee.matchesQuery("food", emptyMap()))
    }

    @Test
    fun matchesQuery_categoryEnumName_withNoLabels() {
        assertTrue(etf.matchesQuery("investments", emptyMap()))
        assertTrue(rent.matchesQuery("Recur", emptyMap()))
    }

    @Test
    fun matchesQuery_amount() {
        assertTrue(coffee.matchesQuery("4,50", emptyMap()))
        assertTrue(coffee.matchesQuery("4.5", emptyMap()))
        assertFalse(coffee.matchesQuery("4.51", emptyMap()))
    }

    @Test
    fun matchesQuery_refund_matchesByMagnitude() {
        assertTrue(coffee.copy(isRefund = true).matchesQuery("4.50", emptyMap()))
    }

    @Test
    fun matchesQuery_queryIsTrimmed() {
        assertTrue(coffee.matchesQuery("  coffee  ", emptyMap()))
    }

    // ---- toListContent ----

    @Test
    fun toListContent_noFilter_returnsEverything_andEchoesFilterState() {
        val content = listOf(coffee, rent, etf).content()

        assertEquals(3, content.expenses.size)
        assertEquals("", content.query)
        assertNull(content.categoryFilter)
        assertEquals(Period.CurrentMonth, content.period)
    }

    @Test
    fun toListContent_categoryFilter_only() {
        val content = listOf(coffee, rent, etf).content(category = ExpenseCategory.RECURRING)

        assertEquals(listOf(rent), content.expenses)
        assertEquals(ExpenseCategory.RECURRING, content.categoryFilter)
    }

    @Test
    fun toListContent_foodCategoryFilter_showsOnlyFood() {
        val lunch = coffee.copy(id = "l", name = "Lunch", category = ExpenseCategory.FOOD)

        val content = listOf(coffee, groceries, rent, etf, lunch).content(category = ExpenseCategory.FOOD)

        assertEquals(setOf(groceries, lunch), content.expenses.toSet())
        assertTrue(content.expenses.all { it.category == ExpenseCategory.FOOD })
        assertEquals(ExpenseCategory.FOOD, content.categoryFilter)
    }

    @Test
    fun toListContent_foodCategoryFilter_noFoodExpenses_isEmpty() {
        assertTrue(listOf(coffee, rent, etf).content(category = ExpenseCategory.FOOD).expenses.isEmpty())
    }

    @Test
    fun toListContent_ptFoodLabelQuery_withNoCategoryFilter_findsFood() {
        val content = listOf(coffee, groceries, rent).content(query = "alimentação", labels = ptLabels)

        assertEquals(listOf(groceries), content.expenses)
    }

    @Test
    fun toListContent_categoryAndQuery_areAnded() {
        val coffeeBeans = coffee.copy(id = "b", name = "Coffee beans", category = ExpenseCategory.RECURRING)

        val content = listOf(coffee, coffeeBeans, rent).content(query = "coffee", category = ExpenseCategory.RECURRING)

        assertEquals(listOf(coffeeBeans), content.expenses)
    }

    @Test
    fun toListContent_blankQuery_isIgnored() {
        assertEquals(3, listOf(coffee, rent, etf).content(query = "   ").expenses.size)
    }

    @Test
    fun toListContent_amountSort_usesSignedAmount_soRefundsSortBelowRegularExpenses() {
        val bigRefund = coffee.copy(id = "refund", amountCents = 100_000L, isRefund = true)
        val items = listOf(coffee, bigRefund, rent)

        assertEquals(listOf(rent, coffee, bigRefund), items.content(sort = SortOption.AMOUNT_DESC).expenses)
        assertEquals(listOf(bigRefund, coffee, rent), items.content(sort = SortOption.AMOUNT_ASC).expenses)
    }

    @Test
    fun toListContent_emptyInput_isEmptyContent() {
        assertTrue(emptyList<Expense>().content(query = "x", category = ExpenseCategory.SHOPPING).expenses.isEmpty())
    }
}
