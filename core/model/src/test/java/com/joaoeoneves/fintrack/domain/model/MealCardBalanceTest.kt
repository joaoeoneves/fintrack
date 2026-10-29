package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MealCardBalanceTest {
    private val at = Instant.parse("2026-10-10T10:00:00Z")

    private fun spent(
        id: String,
        cents: Long,
        card: Boolean,
        refund: Boolean = false,
    ) = testExpense(id, cents, at, isRefund = refund).copy(paidWithMealCard = card)

    @Test
    fun onlyFlaggedExpensesCount() {
        val b = mealCardBalanceOf(10_000, listOf(spent("a", 3_000, true), spent("b", 5_000, false)))

        assertEquals(10_000, b.receivedCents)
        assertEquals(3_000, b.spentCents)
        assertEquals(7_000, b.balanceCents)
    }

    @Test
    fun flaggedRefund_reducesSpent_unflaggedRefundIgnored() {
        val b =
            mealCardBalanceOf(
                10_000,
                listOf(spent("a", 4_000, true), spent("r", 1_000, true, refund = true), spent("x", 900, false, true)),
            )

        assertEquals(3_000, b.spentCents)
        assertEquals(7_000, b.balanceCents)
    }

    @Test
    fun negativeBalance_isKeptAsIs() {
        assertEquals(-2_500, mealCardBalanceOf(1_000, listOf(spent("a", 3_500, true))).balanceCents)
        val withRefund = listOf(spent("a", 1_000, true), spent("r", 500, true, true))
        assertEquals(-500, mealCardBalanceOf(0, withRefund).balanceCents)
    }

    @Test
    fun noExpenses_balanceIsReceived() {
        assertEquals(1_000, mealCardBalanceOf(1_000, emptyList()).balanceCents)
    }

    @Test
    fun usesMealCard_onlyForCardWithPositiveRate() {
        assertTrue(SalaryProfile(mealRatePerDayCents = 600).usesMealCard())
        assertFalse(SalaryProfile(mealRatePerDayCents = 600, mealPayment = MealPayment.WITH_SALARY).usesMealCard())
        assertFalse(SalaryProfile(mealRatePerDayCents = 0).usesMealCard())
        assertFalse(SalaryProfile(mealRatePerDayCents = null).usesMealCard())
        assertFalse((null as SalaryProfile?).usesMealCard())
    }
}
