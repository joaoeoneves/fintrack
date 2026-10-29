package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PeriodYearTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-29T10:00:00Z")

    @Test
    fun yearToken_roundTrips() {
        assertEquals("year:2026", Period.Year(2026).encode())
        assertEquals(Period.Year(2026), decodePeriod("year:2026", now, zone))
    }

    @Test
    fun year_coversJanuaryToDecember() {
        val range = Period.Year(2025).toInstantRange(now, zone)

        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), range.startInclusive)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), range.endExclusive)
    }

    @Test
    fun previousYear_andNextYear_neverPastTheCurrentYear() {
        assertEquals(Period.Year(2025), Period.Year(2026).previousYear())
        assertEquals(Period.Year(2026), Period.Year(2025).nextYear(now, zone))
        assertEquals(Period.Year(2026), Period.Year(2026).nextYear(now, zone))
        assertTrue(Period.Year(2025).canGoToNextYear(now, zone))
        assertFalse(Period.Year(2026).canGoToNextYear(now, zone))
        assertFalse(Period.CurrentMonth.canGoToNextYear(now, zone))
    }
}
