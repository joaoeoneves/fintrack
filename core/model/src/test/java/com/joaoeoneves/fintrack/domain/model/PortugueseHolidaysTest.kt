package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortugueseHolidaysTest {
    @Test
    fun easterSunday_knownYears() {
        assertEquals(LocalDate.of(2024, 3, 31), easterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), easterSunday(2027))
        assertEquals(LocalDate.of(2038, 4, 25), easterSunday(2038))
    }

    @Test
    fun easterSunday_isAlwaysASunday() {
        (1950..2100).forEach { year ->
            assertEquals("Easter $year", java.time.DayOfWeek.SUNDAY, easterSunday(year).dayOfWeek)
        }
    }

    @Test
    fun holidays2026_areThirteenWithTheRightDates() {
        val expected =
            setOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 3), // Good Friday
                LocalDate.of(2026, 4, 5), // Easter Sunday
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 4), // Corpus Christi
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 8),
                LocalDate.of(2026, 12, 25),
            )

        val holidays = portugueseNationalHolidays(2026)

        assertEquals(13, holidays.size)
        assertEquals(expected, holidays)
    }

    @Test
    fun holidays_movableOnesFollowEaster_in2027() {
        val holidays = portugueseNationalHolidays(2027)

        assertTrue(LocalDate.of(2027, 3, 26) in holidays) // Good Friday
        assertTrue(LocalDate.of(2027, 3, 28) in holidays)
        assertTrue(LocalDate.of(2027, 5, 27) in holidays) // Corpus Christi
        assertFalse(LocalDate.of(2027, 4, 3) in holidays)
        assertEquals(13, holidays.size)
    }

    @Test
    fun holidays_allBelongToTheRequestedYear() {
        assertTrue(portugueseNationalHolidays(2030).all { it.year == 2030 })
    }

    @Test
    fun holidays_carnivalIsNotIncluded() {
        // Carnival 2026 is Tuesday 17 Feb: a tolerated day off, not a national holiday.
        assertFalse(LocalDate.of(2026, 2, 17) in portugueseNationalHolidays(2026))
    }
}
