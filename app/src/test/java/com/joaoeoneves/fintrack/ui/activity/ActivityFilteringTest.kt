package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.activity.analytics.NO_ENVELOPE
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

    // FOOD is filed under "env-food"; SHOPPING (and any unknown category) is in no envelope.
    private val envelopeOf: (CategoryId) -> String? = { if (it == BuiltInCategories.FOOD.id) "env-food" else null }

    private fun filter(
        type: ActivityType = ActivityType.ALL,
        category: CategoryId? = null,
        query: String = "",
        list: List<Transaction> = all,
        envelope: String? = null,
    ) = filterTransactions(list, type, category, query, emptyMap(), envelope, envelopeOf)

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

        assertEquals(listOf(lunch), filterTransactions(all, ActivityType.ALL, null, "aliment", labels))
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

    @Test
    fun envelope_withTypeAll_showsOnlyThatEnvelopesExpenses_hidingIncome() {
        assertEquals(listOf(lunch), filter(envelope = "env-food"))
    }

    @Test
    fun envelope_withTypeExpense_narrowsExpenses() {
        assertEquals(listOf(lunch), filter(type = ActivityType.EXPENSE, envelope = "env-food"))
    }

    @Test
    fun envelope_isIgnoredForIncome() {
        assertEquals(listOf(salary, gift), filter(type = ActivityType.INCOME, envelope = "env-food"))
    }

    @Test
    fun noEnvelope_keepsOnlyUnenvelopedExpenses_hidingIncome() {
        assertEquals(listOf(coffee), filter(envelope = NO_ENVELOPE))
    }

    @Test
    fun noEnvelope_isIgnoredForIncome() {
        assertEquals(listOf(salary, gift), filter(type = ActivityType.INCOME, envelope = NO_ENVELOPE))
    }

    @Test
    fun unknownCategory_countsAsNoEnvelope() {
        val ghost = coffee.copy(id = "x", category = Category.unknown(CategoryId("ghost")))

        assertEquals(listOf(ghost), filter(envelope = NO_ENVELOPE, list = listOf(ghost, lunch)))
        assertTrue(filter(envelope = "env-food", list = listOf(ghost)).isEmpty())
    }

    @Test
    fun envelope_withNoMatchingExpense_isEmpty() {
        assertTrue(filter(envelope = "env-other").isEmpty())
    }

    @Test
    fun nullEnvelope_doesNotFilter() {
        assertEquals(all, filter(envelope = null))
    }

    @Test
    fun envelopeAndCategory_areAnded() {
        assertEquals(listOf(lunch), filter(category = BuiltInCategories.FOOD.id, envelope = "env-food"))
        assertTrue(filter(category = BuiltInCategories.SHOPPING.id, envelope = "env-food").isEmpty())
    }

    @Test
    fun envelopeAndQuery_areAnded() {
        assertEquals(listOf(lunch), filter(query = "lunch", envelope = "env-food"))
        assertTrue(filter(query = "coffee", envelope = "env-food").isEmpty())
    }
}
