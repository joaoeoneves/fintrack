package com.joaoeoneves.fintrack.ui.income

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MealCardViewNoAllowanceTest {
    private val from = YearMonth.of(2026, 9)

    @Test
    fun `a month with a zero plafond has no allowance received`() {
        assertTrue(MealCardView(0L, 0L, from).noAllowanceReceived)
    }

    @Test
    fun `spending against a zero plafond is still no allowance received`() {
        assertTrue(MealCardView(0L, 1_500L, from).noAllowanceReceived)
    }

    @Test
    fun `a positive plafond is an allowance`() = assertFalse(MealCardView(1, 0L, from).noAllowanceReceived)

    @Test
    fun `a year view never reports it`() = assertFalse(MealCardView(0L, 0L, null).noAllowanceReceived)
}
