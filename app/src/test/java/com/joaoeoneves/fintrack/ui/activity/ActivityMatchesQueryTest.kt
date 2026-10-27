package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [matchesQuery] for expenses (built-in and user categories) and income. */
class ActivityMatchesQueryTest {
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
    private val rent = coffee.copy(id = "r", name = "Rent", category = BuiltInCategories.RECURRING, note = null)
    private val groceries = coffee.copy(id = "f", name = "Supermarket", category = BuiltInCategories.FOOD, note = null)
    private val ptLabels =
        mapOf(BuiltInCategories.SHOPPING.id to "Compras", BuiltInCategories.FOOD.id to "Alimentação")
    private val gym = userCategory(id = "k9Zx2Qa7", name = "Gym")
    private val fee = coffee.copy(id = "e", name = "Monthly fee", category = gym, note = null)
    private val salary =
        Income(id = "s", source = "Salary", amountCents = 250_000L, date = now, note = "October payroll")
    private val gift = Income(id = "g", source = "Gift", amountCents = 5_050L, date = now, note = null)

    @Test
    fun expense_nameAndNote_caseInsensitive() {
        assertTrue(coffee.matchesQuery("COFFEE", emptyMap()))
        assertTrue(coffee.matchesQuery("OAT", emptyMap()))
        assertFalse(rent.matchesQuery("oat", emptyMap()))
    }

    @Test
    fun expense_localizedLabel() {
        assertTrue(groceries.matchesQuery("aliment", ptLabels))
        assertFalse(coffee.matchesQuery("Alimentação", ptLabels))
    }

    @Test
    fun expense_builtInCategoryId_withNoLabels() {
        assertTrue(groceries.matchesQuery("food", emptyMap()))
        assertFalse(coffee.matchesQuery("food", emptyMap()))
    }

    @Test
    fun expense_amount_dotOrComma_andRefundByMagnitude() {
        assertTrue(coffee.matchesQuery("4,50", emptyMap()))
        assertTrue(coffee.matchesQuery("4.5", emptyMap()))
        assertFalse(coffee.matchesQuery("4.51", emptyMap()))
        assertTrue(coffee.copy(isRefund = true).matchesQuery("4.50", emptyMap()))
    }

    @Test
    fun expense_queryIsTrimmed() {
        assertTrue(coffee.matchesQuery("  coffee  ", emptyMap()))
    }

    @Test
    fun expense_customCategoryName_matches() {
        assertTrue(fee.matchesQuery("gym", emptyMap()))
        assertTrue(fee.matchesQuery("GY", emptyMap()))
    }

    @Test
    fun expense_idKeyedLabel_matches() {
        assertTrue(fee.matchesQuery("fitness", mapOf(gym.id to "Fitness club")))
        assertFalse(fee.matchesQuery("fitness", mapOf(CategoryId("other") to "Fitness club")))
    }

    @Test
    fun expense_generatedCustomId_isNeverMatched() {
        assertFalse(fee.matchesQuery("k9zx", emptyMap()))
    }

    @Test
    fun income_sourceAndNote_caseInsensitive() {
        assertTrue(salary.matchesQuery("SAL"))
        assertFalse(gift.matchesQuery("sal"))
        assertTrue(salary.matchesQuery("payroll"))
        assertFalse(gift.matchesQuery("payroll"))
    }

    @Test
    fun income_amount_dotOrComma() {
        assertTrue(gift.matchesQuery("50.50"))
        assertTrue(gift.matchesQuery("50,5"))
        assertTrue(salary.matchesQuery("2500"))
        assertFalse(gift.matchesQuery("50.51"))
    }
}
