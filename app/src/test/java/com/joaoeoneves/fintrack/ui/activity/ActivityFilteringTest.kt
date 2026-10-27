package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [filterTransactions] over mixed expense and income lists. */
class ActivityFilteringTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private val coffee =
        Expense(
            id = "c",
            name = "Morning Coffee",
            amountCents = 450L,
            category = BuiltInCategories.SHOPPING,
            date = now,
            note = "oat milk",
        )
    private val lunch = coffee.copy(id = "l", name = "Lunch", category = BuiltInCategories.FOOD, note = null)
    private val salary =
        Income(id = "s", source = "Salary", amountCents = 250_000L, date = now, note = "October payroll")
    private val gift = Income(id = "g", source = "Gift", amountCents = 5_050L, date = now, note = null)
    private val all: List<Transaction> = listOf(coffee, lunch, salary, gift)

    private fun filter(
        type: ActivityType = ActivityType.ALL,
        category: CategoryId? = null,
        query: String = "",
        labels: Map<CategoryId, String> = emptyMap(),
        list: List<Transaction> = all,
    ) = filterTransactions(list, type, category, query, labels)

    @Test
    fun noFilters_returnsEverything() {
        assertEquals(all, filter())
    }

    @Test
    fun emptyList_isEmpty() {
        assertTrue(filter(query = "x", category = BuiltInCategories.FOOD.id, list = emptyList()).isEmpty())
    }

    @Test
    fun typeFilter_expense_keepsOnlyExpenses() {
        assertEquals(listOf(coffee, lunch), filter(type = ActivityType.EXPENSE))
    }

    @Test
    fun typeFilter_income_keepsOnlyIncome() {
        assertEquals(listOf(salary, gift), filter(type = ActivityType.INCOME))
    }

    @Test
    fun category_withTypeAll_showsOnlyThatCategorysExpenses_hidingIncome() {
        assertEquals(listOf(lunch), filter(category = BuiltInCategories.FOOD.id))
    }

    @Test
    fun category_withTypeExpense_narrowsExpenses() {
        assertEquals(listOf(coffee), filter(type = ActivityType.EXPENSE, category = BuiltInCategories.SHOPPING.id))
    }

    @Test
    fun category_isIgnoredForIncome() {
        assertEquals(listOf(salary, gift), filter(type = ActivityType.INCOME, category = BuiltInCategories.FOOD.id))
    }

    @Test
    fun category_withNoMatchingExpense_isEmpty() {
        assertTrue(filter(category = BuiltInCategories.INVESTMENTS.id).isEmpty())
    }

    @Test
    fun query_blank_isIgnored() {
        assertEquals(all, filter(query = "   "))
    }

    @Test
    fun query_matchesExpenseName_caseInsensitive() {
        assertEquals(listOf(coffee), filter(query = "COFFEE"))
    }

    @Test
    fun query_matchesIncomeSource() {
        assertEquals(listOf(salary), filter(query = "sal"))
    }

    @Test
    fun query_matchesNotes_ofBothTypes() {
        assertEquals(listOf(coffee), filter(query = "oat"))
        assertEquals(listOf(salary), filter(query = "payroll"))
    }

    @Test
    fun query_matchesLocalizedCategoryLabel_forExpensesOnly() {
        val labels = mapOf(BuiltInCategories.FOOD.id to "Alimentação")

        assertEquals(listOf(lunch), filter(query = "aliment", labels = labels))
    }

    @Test
    fun query_matchesAmount_acrossTypes() {
        assertEquals(listOf(coffee, lunch), filter(query = "4,50"))
        assertEquals(listOf(gift), filter(query = "50.50"))
        assertEquals(listOf(salary), filter(query = "2500"))
    }

    @Test
    fun query_noMatch_isEmpty() {
        assertTrue(filter(query = "zzz").isEmpty())
    }

    @Test
    fun typeCategoryAndQuery_areAnded() {
        val beans = coffee.copy(id = "b", name = "Coffee beans", category = BuiltInCategories.FOOD)
        val list = listOf(coffee, beans, salary)

        assertEquals(
            listOf(beans),
            filter(type = ActivityType.EXPENSE, category = BuiltInCategories.FOOD.id, query = "coffee", list = list),
        )
    }
}
