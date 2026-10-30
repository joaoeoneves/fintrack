package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringCostTest {
    private val today = day(2026, 10, 30)

    @Test
    fun splitsMonthlyAndYearlyAndDerivesAnnualFigures() {
        val items =
            listOf(
                recurringItem(id = "a", amountCents = 50_000),
                recurringItem(id = "b", amountCents = 1_000),
                recurringItem(id = "c", amountCents = 12_000, frequency = RecurringFrequency.YEARLY),
            )
        val cost = recurringCostOf(items, today)
        assertEquals(51_000L, cost.monthlyItemsCents)
        assertEquals(12_000L, cost.yearlyItemsCents)
        assertEquals(624_000L, cost.annualCents)
        assertEquals(52_000L, cost.monthlyEquivalentCents)
        assertEquals(1_000L, cost.yearlyPerMonthCents)
    }

    @Test
    fun excludesPausedEndedAndIncomeItems_butKeepsOneEndingToday() {
        val items =
            listOf(
                recurringItem(id = "paused", amountCents = 100, active = false),
                recurringItem(id = "ended", amountCents = 200, endDate = day(2026, 10, 29)),
                recurringItem(id = "income", amountCents = 400, type = RecurringType.INCOME),
                recurringItem(id = "lastDay", amountCents = 800, endDate = today),
            )
        val cost = recurringCostOf(items, today)
        assertEquals(800L, cost.monthlyItemsCents)
        assertEquals(0L, cost.yearlyItemsCents)
    }

    @Test
    fun emptyIsZero() {
        assertEquals(RecurringCost(0, 0), recurringCostOf(emptyList(), today))
    }

    @Test
    fun monthlyEquivalentRoundsHalfUp() {
        // 1_000 / 12 = 83.33 -> 83 ; 1_006 / 12 = 83.83 -> 84 ; 1_002 / 12 = 83.5 -> 84.
        assertEquals(83L, RecurringCost(0, 1_000).yearlyPerMonthCents)
        assertEquals(84L, RecurringCost(0, 1_006).yearlyPerMonthCents)
        assertEquals(84L, RecurringCost(0, 1_002).yearlyPerMonthCents)
    }
}
