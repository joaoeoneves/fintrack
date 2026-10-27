package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** [annualizedCents]: monthly x12, yearly x1, per type, active and not ended only. */
class RecurringAnnualizedTest {
    private val today = day(2026, 6, 1)
    private val monthly = recurringItem(id = "m", amountCents = 1_000)
    private val yearly = recurringItem(id = "y", amountCents = 5_000, frequency = RecurringFrequency.YEARLY)
    private val salary = recurringItem(id = "s", type = RecurringType.INCOME, amountCents = 200_000)

    @Test
    fun empty_isZero() {
        assertEquals(0L, annualizedCents(emptyList(), RecurringType.EXPENSE, today))
    }

    @Test
    fun monthlyCountsTwelveTimes_yearlyOnce() {
        assertEquals(12_000L, annualizedCents(listOf(monthly), RecurringType.EXPENSE, today))
        assertEquals(5_000L, annualizedCents(listOf(yearly), RecurringType.EXPENSE, today))
        assertEquals(17_000L, annualizedCents(listOf(monthly, yearly), RecurringType.EXPENSE, today))
    }

    @Test
    fun typesAreSeparate() {
        val all = listOf(monthly, salary)

        assertEquals(12_000L, annualizedCents(all, RecurringType.EXPENSE, today))
        assertEquals(2_400_000L, annualizedCents(all, RecurringType.INCOME, today))
    }

    @Test
    fun pausedItems_areExcluded() {
        assertEquals(0L, annualizedCents(listOf(monthly.copy(active = false)), RecurringType.EXPENSE, today))
    }

    @Test
    fun endedItems_areExcluded_butEndingTodayCounts() {
        val ended = monthly.copy(endDate = day(2026, 5, 31))
        val endsToday = monthly.copy(endDate = today)

        assertEquals(0L, annualizedCents(listOf(ended), RecurringType.EXPENSE, today))
        assertEquals(12_000L, annualizedCents(listOf(endsToday), RecurringType.EXPENSE, today))
    }

    @Test
    fun largeAmounts_doNotOverflowInt() {
        val big = monthly.copy(amountCents = 1_000_000_000_000L)

        assertEquals(12_000_000_000_000L, annualizedCents(listOf(big), RecurringType.EXPENSE, today))
    }
}
