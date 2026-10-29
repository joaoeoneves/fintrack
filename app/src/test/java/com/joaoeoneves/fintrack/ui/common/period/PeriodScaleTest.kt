package com.joaoeoneves.fintrack.ui.common.period

import com.joaoeoneves.fintrack.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class PeriodScaleTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-29T10:00:00Z")

    @Test
    fun scale_ofEachPeriod() {
        assertEquals(PeriodScale.YEAR, Period.Year(2026).scale)
        assertEquals(PeriodScale.MONTH, Period.CurrentMonth.scale)
        assertEquals(PeriodScale.MONTH, Period.Month(YearMonth.of(2026, 3)).scale)
    }

    @Test
    fun monthToYear_keepsTheAnchorYear() {
        assertEquals(Period.Year(2026), Period.CurrentMonth.switchedTo(PeriodScale.YEAR, now, zone))
        assertEquals(Period.Year(2025), Period.Month(YearMonth.of(2025, 4)).switchedTo(PeriodScale.YEAR, now, zone))
    }

    @Test
    fun yearToMonth_currentYearIsTheCurrentMonth_pastYearIsJanuary() {
        assertEquals(Period.CurrentMonth, Period.Year(2026).switchedTo(PeriodScale.MONTH, now, zone))
        assertEquals(
            Period.Month(YearMonth.of(2024, 1)),
            Period.Year(2024).switchedTo(PeriodScale.MONTH, now, zone),
        )
    }

    @Test
    fun sameScale_isUnchanged() {
        val month = Period.Month(YearMonth.of(2026, 3))
        assertEquals(month, month.switchedTo(PeriodScale.MONTH, now, zone))
        assertEquals(Period.Year(2025), Period.Year(2025).switchedTo(PeriodScale.YEAR, now, zone))
    }

    @Test
    fun futureYearToMonth_withMonthsAhead_isJanuaryOfThatYear() {
        assertEquals(
            Period.Month(YearMonth.of(2028, 1)),
            Period.Year(2028).switchedTo(PeriodScale.MONTH, now, zone, monthsAhead = 120),
        )
    }
}
