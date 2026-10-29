package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Period navigation with `monthsAhead = 1` (planning screens may open the next month). */
class PeriodAheadTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val november = Period.Month(YearMonth.of(2026, 11))

    @Test
    fun currentMonth_canGoNext_onlyWhenAhead() {
        assertTrue(Period.CurrentMonth.canGoToNextMonth(now, zone, monthsAhead = 1))
        assertFalse(Period.CurrentMonth.canGoToNextMonth(now, zone))
    }

    @Test
    fun nextMonth_cannotGoFurther() {
        assertFalse(november.canGoToNextMonth(now, zone, monthsAhead = 1))
        assertEquals(november, november.nextMonth(now, zone, monthsAhead = 1))
    }

    @Test
    fun nextOfCurrent_isNovember() {
        assertEquals(november, Period.CurrentMonth.nextMonth(now, zone, monthsAhead = 1))
    }

    @Test
    fun nextOfSeptember_isTheCurrentMonth() {
        val september = Period.Month(YearMonth.of(2026, 9))
        assertEquals(Period.CurrentMonth, september.nextMonth(now, zone, monthsAhead = 1))
    }

    @Test
    fun ofMonth_withAhead_keepsNextMonthButNotFurther() {
        assertEquals(november, Period.ofMonth(YearMonth.of(2026, 11), now, zone, monthsAhead = 1))
        assertEquals(Period.CurrentMonth, Period.ofMonth(YearMonth.of(2026, 12), now, zone, monthsAhead = 1))
        assertEquals(Period.CurrentMonth, Period.ofMonth(YearMonth.of(2026, 11), now, zone))
    }

    @Test
    fun decode_nextMonthToken_followsMonthsAhead() {
        assertEquals(november, decodePeriod("month:2026-11", now, zone, monthsAhead = 1))
        assertEquals(Period.CurrentMonth, decodePeriod("month:2026-11", now, zone))
    }

    @Test
    fun decode_decemberToken_isCurrentMonth() {
        assertEquals(Period.CurrentMonth, decodePeriod("month:2026-12", now, zone, monthsAhead = 1))
    }
}
