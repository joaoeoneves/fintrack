package com.joaoeoneves.fintrack.ui.detail

import com.joaoeoneves.fintrack.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class DetailWindowTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val oct = YearMonth.of(2026, 10)

    @Test
    fun monthPeriod_chartsTwelveMonthsEndingAtTheRouteMonth() {
        val months = chartMonthsOf(Period.CurrentMonth, oct, 12, now, zone)

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2025, 11), months.first())
        assertEquals(oct, months.last())
    }

    @Test
    fun selectedMonthInsideTheWindow_doesNotSlideIt() {
        val months = chartMonthsOf(Period.Month(YearMonth.of(2026, 3)), oct, 12, now, zone)

        assertEquals(oct, months.last())
    }

    @Test
    fun selectedMonthBeforeTheWindow_slidesItBack() {
        val months = chartMonthsOf(Period.Month(YearMonth.of(2025, 6)), oct, 12, now, zone)

        assertEquals(YearMonth.of(2025, 6), months.first())
        assertEquals(12, months.size)
    }

    @Test
    fun yearPeriod_chartsItsMonthsUpToTheCurrentOne() {
        val past = chartMonthsOf(Period.Year(2025), oct, 12, now, zone)
        val current = chartMonthsOf(Period.Year(2026), oct, 12, now, zone)

        assertEquals(YearMonth.of(2025, 1)..YearMonth.of(2025, 12), past.first()..past.last())
        assertEquals(12, past.size)
        assertEquals(10, current.size)
        assertEquals(oct, current.last())
    }

    @Test
    fun routeEnd_isTheMonthOrTheLastMonthOfTheYear() {
        assertEquals(oct, routeEndOf(Period.CurrentMonth, now, zone))
        assertEquals(YearMonth.of(2026, 3), routeEndOf(Period.Month(YearMonth.of(2026, 3)), now, zone))
        assertEquals(YearMonth.of(2025, 12), routeEndOf(Period.Year(2025), now, zone))
        assertEquals(oct, routeEndOf(Period.Year(2026), now, zone))
    }
}
