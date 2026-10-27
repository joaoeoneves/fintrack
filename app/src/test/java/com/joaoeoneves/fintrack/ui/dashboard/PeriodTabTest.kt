package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodTabTest {
    @Test
    fun currentAndPastMonth_mapToOneMonth() {
        assertEquals(PeriodTab.ONE_MONTH, Period.CurrentMonth.toTab())
        assertEquals(PeriodTab.ONE_MONTH, Period.Month(YearMonth.of(2025, 2)).toTab())
    }

    @Test
    fun rolling_mapsToMatchingTab() {
        assertEquals(PeriodTab.THREE_MONTHS, Period.Rolling(TimeRange.THREE_MONTHS).toTab())
        assertEquals(PeriodTab.SIX_MONTHS, Period.Rolling(TimeRange.SIX_MONTHS).toTab())
        assertEquals(PeriodTab.ONE_YEAR, Period.Rolling(TimeRange.ONE_YEAR).toTab())
    }

    @Test
    fun custom_mapsToNull() {
        assertNull(Period.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)).toTab())
    }

    @Test
    fun toPeriod_values() {
        assertEquals(Period.CurrentMonth, PeriodTab.ONE_MONTH.toPeriod())
        assertEquals(Period.Rolling(TimeRange.THREE_MONTHS), PeriodTab.THREE_MONTHS.toPeriod())
        assertEquals(Period.Rolling(TimeRange.SIX_MONTHS), PeriodTab.SIX_MONTHS.toPeriod())
        assertEquals(Period.Rolling(TimeRange.ONE_YEAR), PeriodTab.ONE_YEAR.toPeriod())
    }

    @Test
    fun roundTrip_everyTab() {
        PeriodTab.entries.forEach { assertEquals(it, it.toPeriod().toTab()) }
    }
}
