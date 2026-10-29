package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The current month is partial only while a line due in it is still unreceived. */
class YearIncomeOutlookPartialTest {
    private val today = YearMonth.of(2026, 10)
    private val im = IncomeMonth { 20 }
    private val salary = PlannedIncome("s", "Salary", 100_000L, 0)
    private val meal = PlannedIncome("m", "Meal", 900L, 1, basis = IncomeBasis.PER_WORKING_DAY)

    @Test
    fun everythingReceived_isNotPartial() {
        val received = mapOf(today to 118_000L)
        val month = yearIncomeOutlook(2026, listOf(salary, meal), im, received, today, setOf("s", "m")).months[9]

        assertFalse(month.partial)
        assertTrue(month.received)
        assertEquals(118_000L, month.cents)
    }

    @Test
    fun oneRowPending_isPartial() {
        val received = mapOf(today to 100_000L)
        val month = yearIncomeOutlook(2026, listOf(salary, meal), im, received, today, setOf("s")).months[9]

        assertTrue(month.partial)
        assertFalse(month.received)
        assertEquals(100_000L, month.receivedPartCents)
        assertEquals(118_000L, month.cents)
    }

    @Test
    fun aPendingLineThatPaysNothingThisMonth_doesNotMakeItPartial() {
        val yearly = PlannedIncome("y", "Bonus", 50_000L, 2, frequency = IncomeFrequency.YEARLY, monthOfYear = 6)

        val received = mapOf(today to 100_000L)
        val month = yearIncomeOutlook(2026, listOf(salary, yearly), im, received, today, setOf("s")).months[9]

        assertFalse(month.partial)
        assertEquals(100_000L, month.cents)
    }
}
