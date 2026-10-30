package com.joaoeoneves.fintrack.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class IncomeLineHelpersTest {
    @Test
    fun fixedOnForNewLine_defaultsToDayOneAndEarlierIfClosed() {
        val result = IncomeSchedule().withFixedPayDay(fixed = true, isNewLine = true)
        assertEquals(1, result.payDay)
        assertTrue(result.payDayEarlierIfClosed)
    }

    @Test
    fun fixedOnForExistingLine_keepsItsChoiceOfEarlierIfClosed() {
        val result = IncomeSchedule().withFixedPayDay(fixed = true, isNewLine = false)
        assertEquals(1, result.payDay)
        assertFalse(result.payDayEarlierIfClosed)
    }

    @Test
    fun fixedOn_keepsExistingDayAndFlag() {
        val off = IncomeSchedule(payDay = 15, payDayEarlierIfClosed = false)
        assertEquals(off, off.withFixedPayDay(fixed = true, isNewLine = true))
        val on = IncomeSchedule(payDay = 15, payDayEarlierIfClosed = true)
        assertEquals(on, on.withFixedPayDay(fixed = true, isNewLine = false))
    }

    @Test
    fun fixedOff_clearsDayAndFlag() {
        val result = IncomeSchedule(payDay = 15, payDayEarlierIfClosed = true).withFixedPayDay(false, true)
        assertNull(result.payDay)
        assertFalse(result.payDayEarlierIfClosed)
    }

    @Test
    fun fixedOff_keepsOtherFields() {
        val schedule = IncomeSchedule(autoRecord = true, skipMonths = setOf(8), payDay = 5)
        val result = schedule.withFixedPayDay(false, false)
        assertTrue(result.autoRecord)
        assertEquals(setOf(8), result.skipMonths)
    }

    @Test
    fun defaultEffectiveFrom_futureViewedMonthWins() {
        assertEquals(YearMonth.of(2027, 1), defaultEffectiveFrom(YearMonth.of(2027, 1), YearMonth.of(2026, 10)))
    }

    @Test
    fun defaultEffectiveFrom_currentOrPastViewedMonthGivesNow() {
        val now = YearMonth.of(2026, 10)
        assertEquals(now, defaultEffectiveFrom(now, now))
        assertEquals(now, defaultEffectiveFrom(YearMonth.of(2026, 3), now))
        assertEquals(now, defaultEffectiveFrom(YearMonth.of(2025, 12), now))
    }
}
