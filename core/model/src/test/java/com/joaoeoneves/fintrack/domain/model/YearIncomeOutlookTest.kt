package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class YearIncomeOutlookTest {
    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val utc = ZoneOffset.UTC
    private val today = YearMonth.of(2026, 10)
    private val im = IncomeMonth { 20 }

    @Test
    fun receivedUpToToday_expectedAfter() {
        val line = PlannedIncome("s", "Salary", 100_000L, 0)
        val received = mapOf(YearMonth.of(2026, 1) to 90_000L, today to 95_000L)
        val o = yearIncomeOutlook(2026, listOf(line), im, received, today)

        assertEquals(12, o.months.size)
        assertTrue(o.months.take(10).all { it.received })
        assertTrue(o.months.drop(10).none { it.received })
        assertEquals(90_000L, o.months[0].cents)
        assertEquals(0L, o.months[1].cents)
        assertEquals(95_000L, o.months[9].cents)
        assertEquals(100_000L, o.months[10].cents)
        assertEquals(185_000L, o.receivedCents)
        assertEquals(200_000L, o.expectedCents)
        assertEquals(385_000L, o.totalCents)
    }

    @Test
    fun futureYear_isAllExpected_withExtrasInTheirMonths() {
        val salary = PlannedIncome("s", "Salary", 100_000L, 0)
        val extra = PlannedIncome("e", "Bonus", 50_000L, 1, frequency = IncomeFrequency.YEARLY, monthOfYear = 6)
        val o = yearIncomeOutlook(2027, listOf(salary, extra), im, emptyMap(), today)

        assertTrue(o.months.none { it.received })
        assertEquals(150_000L, o.months[5].cents)
        assertEquals(100_000L, o.months[0].cents)
        assertEquals(1_250_000L, o.totalCents)
        assertEquals(0L, o.receivedCents)
    }

    @Test
    fun versionChangeMidYear_appliesFromItsMonth() {
        val line =
            PlannedIncome(
                "s",
                "Salary",
                120_000L,
                0,
                versions = listOf(AmountVersion(YearMonth.of(2026, 12), 120_000L), AmountVersion(YearMonth.of(2020, 1), 100_000L)),
            )
        val o = yearIncomeOutlook(2026, listOf(line), im, emptyMap(), YearMonth.of(2026, 9))

        assertEquals(100_000L, o.months[9].cents)
        assertEquals(100_000L, o.months[10].cents)
        assertEquals(120_000L, o.months[11].cents)
        assertFalse(o.months[11].received)
    }

    @Test
    fun yearNavigation_withMonthsAhead() {
        val y26 = Period.Year(2026)
        assertFalse(y26.canGoToNextYear(now, utc))
        assertEquals(y26, y26.nextYear(now, utc))
        // October 2026 + 3 months = January 2027.
        assertFalse(y26.canGoToNextYear(now, utc, 2))
        assertTrue(y26.canGoToNextYear(now, utc, 3))
        assertEquals(Period.Year(2027), y26.nextYear(now, utc, 3))
        assertEquals(Period.Year(2027), y26.nextYear(now, utc, 120))
        assertFalse(Period.Year(2036).canGoToNextYear(now, utc, 120))
        assertTrue(Period.Year(2035).canGoToNextYear(now, utc, 120))
    }
}
