package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The meal-card switch of the expense form: when it shows (N13) and how the used-up plafond changes the prefill. */
class MealCardSwitchStateTest {
    private val food = BuiltInCategories.FOOD.copy(mealCardByDefault = true)

    @Test
    fun switchShows_whenTheCardApplies() {
        assertTrue(ExpenseFormState(mealCardAvailable = true).showsMealCardSwitch)
        assertFalse(ExpenseFormState().showsMealCardSwitch)
    }

    @Test
    fun switchShows_forAnEditedExpenseAlreadyOnTheCard_evenWhenTheCardIsOff() {
        val edited = ExpenseFormState(isEditMode = true, paidWithMealCard = true, mealCardAvailable = false)

        assertTrue(edited.showsMealCardSwitch)
        assertFalse(edited.copy(paidWithMealCard = false).showsMealCardSwitch)
        assertFalse(edited.copy(isEditMode = false).showsMealCardSwitch)
    }

    @Test
    fun usedUpPlafond_defaultsANewExpenseToTheBank() {
        assertFalse(mealCardPrefill(food, isRefund = false, available = true, plafondUsed = true))
        assertTrue(mealCardPrefill(food, isRefund = false, available = true, plafondUsed = false))
    }

    @Test
    fun withPrefill_followsThePlafondUsedFlag_untilTheSwitchIsTouched() {
        val base = ExpenseFormState(category = food, mealCardAvailable = true)

        assertTrue(base.withMealCardPrefill().paidWithMealCard)
        assertFalse(base.copy(mealCardPlafondUsed = true).withMealCardPrefill().paidWithMealCard)
        // The user's choice wins over the flag, as does an expense being edited.
        val chosen = base.copy(mealCardPlafondUsed = true, mealCardTouched = true, paidWithMealCard = true)
        assertTrue(chosen.withMealCardPrefill().paidWithMealCard)
        val edited = base.copy(isEditMode = true, mealCardPlafondUsed = true, paidWithMealCard = true)
        assertEquals(true, edited.withMealCardPrefill().paidWithMealCard)
    }

    @Test
    fun refund_staysOnTheBankWhateverThePlafond() {
        val refund = ExpenseFormState(category = food, mealCardAvailable = true, isRefund = true)

        assertFalse(refund.withMealCardPrefill().paidWithMealCard)
        assertFalse(refund.copy(mealCardPlafondUsed = true).withMealCardPrefill().paidWithMealCard)
    }
}
