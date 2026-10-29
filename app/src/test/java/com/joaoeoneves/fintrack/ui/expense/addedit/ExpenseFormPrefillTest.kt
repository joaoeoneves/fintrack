package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExpenseFormPrefillTest {
    private val active = userCategory("groceries")
    private val archived = userCategory("old", archived = true)
    private val catalog = listOf(active, archived)
    private val base = ExpenseFormState(name = "x", amountCents = 700L, date = Instant.parse("2026-10-01T10:00:00Z"))

    @Test
    fun `an active category is applied and flagged as suggested`() {
        val form = base.withPrefill("Lidl", 1_250L, "groceries", catalog)
        assertEquals("Lidl", form.name)
        assertEquals(1_250L, form.amountCents)
        assertEquals(active, form.category)
        assertTrue(form.suggestedCategory)
        assertEquals(base.date, form.date)
    }

    @Test
    fun `an archived or unknown category leaves the current one and is not flagged`() {
        listOf("old", "gone").forEach { id ->
            val form = base.withPrefill("Lidl", 1_250L, id, catalog)
            assertEquals(base.category, form.category)
            assertFalse(form.suggestedCategory)
        }
    }

    @Test
    fun `no category id is not a suggestion`() {
        assertFalse(base.withPrefill("Lidl", null, null, catalog).suggestedCategory)
    }

    @Test
    fun `an amount of zero or below keeps the current amount`() {
        assertEquals(700L, base.withPrefill(null, 0L, null, catalog).amountCents)
        assertEquals(700L, base.withPrefill(null, -5L, null, catalog).amountCents)
    }

    @Test
    fun `an amount above the maximum is ignored and the maximum itself is kept`() {
        assertEquals(700L, base.withPrefill(null, MAX_AMOUNT_CENTS + 1, null, catalog).amountCents)
        assertEquals(MAX_AMOUNT_CENTS, base.withPrefill(null, MAX_AMOUNT_CENTS, null, catalog).amountCents)
        assertEquals(1L, base.withPrefill(null, 1L, null, catalog).amountCents)
    }

    @Test
    fun `a null name keeps the current one and a long one is cut`() {
        assertEquals("x", base.withPrefill(null, null, null, catalog).name)
        val long = "a".repeat(MAX_NAME_LENGTH + 20)
        assertEquals(MAX_NAME_LENGTH, base.withPrefill(long, null, null, catalog).name.length)
    }

    @Test
    fun `a prefilled meal category turns the card on only while available and the plafond is not used up`() {
        val food = BuiltInCategories.FOOD.copy(mealCardByDefault = true)
        val meals = listOf(food)
        val available = base.copy(mealCardAvailable = true)

        assertTrue(available.withPrefill("Lunch", 900L, food.id.value, meals).withMealCardPrefill().paidWithMealCard)
        assertFalse(
            available
                .copy(mealCardPlafondUsed = true)
                .withPrefill("Lunch", 900L, food.id.value, meals)
                .withMealCardPrefill()
                .paidWithMealCard,
        )
        assertFalse(base.withPrefill("Lunch", 900L, food.id.value, meals).withMealCardPrefill().paidWithMealCard)
    }
}
