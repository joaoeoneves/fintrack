package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.MealPayment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryMealTaxableWarningTest {
    private fun form(
        meal: Long,
        payment: MealPayment,
    ) = SalaryForm(meal = meal, mealPayment = payment)

    @Test
    fun withSalary_aboveLimit_warns() {
        assertTrue(form(MEAL_CASH_LIMIT_CENTS + 1, MealPayment.WITH_SALARY).mealTaxableWarning())
    }

    @Test
    fun withSalary_atOrBelowLimit_doesNotWarn() {
        assertFalse(form(MEAL_CASH_LIMIT_CENTS, MealPayment.WITH_SALARY).mealTaxableWarning())
        assertFalse(form(450L, MealPayment.WITH_SALARY).mealTaxableWarning())
        assertFalse(form(0L, MealPayment.WITH_SALARY).mealTaxableWarning())
    }

    @Test
    fun card_neverWarns() {
        assertFalse(form(MEAL_CASH_LIMIT_CENTS + 1, MealPayment.CARD).mealTaxableWarning())
        assertFalse(form(5_000L, MealPayment.CARD).mealTaxableWarning())
    }
}
