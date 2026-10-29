package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The current month of a year outlook is partial: what is received plus what its lines still expect. */
class YearIncomeOutlookCurrentMonthTest {
    private val today = YearMonth.of(2026, 10)
    private val im = IncomeMonth { 20 }
    private val salary = PlannedIncome("s", "Salary", 100_000L, 0)
    private val meal = PlannedIncome("m", "Meal", 900L, 1, basis = IncomeBasis.PER_WORKING_DAY)

    @Test
    fun currentMonth_isReceivedPlusPending_andCountsInBothTotals() {
        val o = yearIncomeOutlook(2026, listOf(salary, meal), im, mapOf(today to 100_000L), today, setOf("s"))
        val month = o.months[9]

        assertTrue(month.partial)
        assertFalse(month.received)
        assertEquals(100_000L, month.receivedPartCents)
        assertEquals(118_000L, month.cents)
        assertEquals(100_000L, o.receivedCents)
        assertEquals(18_000L + 2 * (100_000L + 18_000L), o.expectedCents)
        assertEquals(o.receivedCents + o.expectedCents, o.totalCents)
    }

    @Test
    fun currentMonth_withNothingReceived_isAllPending() {
        val month = yearIncomeOutlook(2026, listOf(salary), im, emptyMap(), today).months[9]
        assertEquals(0L, month.receivedPartCents)
        assertEquals(100_000L, month.cents)
    }

    @Test
    fun currentMonth_everyLineReceived_hasNothingPending() {
        val o = yearIncomeOutlook(2026, listOf(salary), im, mapOf(today to 95_000L), today, setOf("s"))
        assertEquals(95_000L, o.months[9].cents)
        assertEquals(95_000L, o.receivedCents)
    }
}
