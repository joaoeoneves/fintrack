package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealCardPrefillTest {
    private val food = BuiltInCategories.FOOD.copy(mealCardByDefault = true)

    @Test
    fun spendingCategoryWithDefault_prefills() = assertTrue(mealCardPrefill(food, isRefund = false, available = true))

    @Test
    fun refund_neverPrefills() = assertFalse(mealCardPrefill(food, isRefund = true, available = true))

    @Test
    fun unavailable_neverPrefills() = assertFalse(mealCardPrefill(food, isRefund = false, available = false))

    @Test
    fun categoryWithoutDefault_doesNotPrefill() =
        assertFalse(mealCardPrefill(food.copy(mealCardByDefault = false), isRefund = false, available = true))

    @Test
    fun savingKind_neverPrefills() =
        assertFalse(mealCardPrefill(food.copy(kind = CategoryKind.SAVING), isRefund = false, available = true))

    @Test
    fun withPrefill_respectsTouchedAndEditMode() {
        val base = ExpenseFormState(category = food, mealCardAvailable = true)

        assertTrue(base.withMealCardPrefill().paidWithMealCard)
        assertFalse(base.copy(mealCardTouched = true).withMealCardPrefill().paidWithMealCard)
        assertTrue(base.copy(isEditMode = true, paidWithMealCard = true).withMealCardPrefill().paidWithMealCard)
        assertEquals(false, base.copy(isRefund = true).withMealCardPrefill().paidWithMealCard)
    }
}
