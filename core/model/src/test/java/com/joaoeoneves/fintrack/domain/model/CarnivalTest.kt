package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CarnivalTest {
    @Test
    fun carnivalTuesday_knownDates() {
        assertEquals(LocalDate.of(2026, 2, 17), carnivalTuesday(2026))
        assertEquals(LocalDate.of(2027, 2, 9), carnivalTuesday(2027))
        assertEquals(LocalDate.of(2025, 3, 4), carnivalTuesday(2025))
        assertEquals(LocalDate.of(2024, 2, 13), carnivalTuesday(2024))
    }

    @Test
    fun carnivalTuesday_isAlwaysATuesday() {
        (2000..2100).forEach { assertEquals("$it", DayOfWeek.TUESDAY, carnivalTuesday(it).dayOfWeek) }
    }

    @Test
    fun carnival_isNotANationalHoliday() {
        (2024..2027).forEach { assertFalse("$it", carnivalTuesday(it) in portugueseNationalHolidays(it)) }
    }
}
