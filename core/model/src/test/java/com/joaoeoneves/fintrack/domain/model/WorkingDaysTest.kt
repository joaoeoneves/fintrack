package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WorkingDaysTest {
    @Test
    fun october2026_has22Weekdays_minusTheMondayHolidayOn5th() {
        // 1-2 (Thu, Fri) + four full weeks = 22 weekdays; Mon 5 Oct is a holiday.
        assertEquals(21, workingDaysIn(YearMonth.of(2026, 10)))
        assertEquals(1, holidayWeekdaysIn(YearMonth.of(2026, 10)))
    }

    @Test
    fun weekendHoliday_isNotSubtractedTwice() {
        // 1 Nov 2026 is a Sunday: the month keeps all 21 weekdays.
        assertEquals(21, workingDaysIn(YearMonth.of(2026, 11)))
        assertEquals(0, holidayWeekdaysIn(YearMonth.of(2026, 11)))
    }

    @Test
    fun april2026_onlyGoodFridayCounts_easterSundayAnd25thAreWeekend() {
        // 22 weekdays; Fri 3 Apr is a holiday, Sun 5 and Sat 25 are not weekdays anyway.
        assertEquals(21, workingDaysIn(YearMonth.of(2026, 4)))
        assertEquals(1, holidayWeekdaysIn(YearMonth.of(2026, 4)))
    }

    @Test
    fun december2026_hasThreeWeekdayHolidays() {
        assertEquals(20, workingDaysIn(YearMonth.of(2026, 12)))
        assertEquals(3, holidayWeekdaysIn(YearMonth.of(2026, 12)))
    }

    @Test
    fun customHolidaySet_isUsed() {
        val month = YearMonth.of(2026, 10)

        assertEquals(22, workingDaysIn(month, emptySet()))
        assertEquals(20, workingDaysIn(month, setOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7))))
        assertEquals(0, holidayWeekdaysIn(month, emptySet()))
    }

    @Test
    fun february2026_hasExactlyFourFullWeeks() {
        assertEquals(20, workingDaysIn(YearMonth.of(2026, 2)))
    }

    @Test
    fun effectiveWorkingDays_overrideWins_includingZero() {
        val month = YearMonth.of(2026, 10)

        assertEquals(15, effectiveWorkingDays(month, 15))
        assertEquals(0, effectiveWorkingDays(month, 0))
        assertEquals(21, effectiveWorkingDays(month, null))
    }

    @Test
    fun workingDays_neverExceedTheMaxWorkingDaysCap() {
        (2024..2030).forEach { year ->
            (1..12).forEach { m ->
                val days = workingDaysIn(YearMonth.of(year, m), emptySet())
                assertEquals("$year-$m", true, days in 20..MAX_WORKING_DAYS)
            }
        }
    }

    @Test
    fun effectiveWorkingDays_subtractsDaysOffFromTheCalendarCount() {
        val month = YearMonth.of(2026, 10)

        assertEquals(18, effectiveWorkingDays(month, null, 3))
        assertEquals(21, effectiveWorkingDays(month, null, 0))
    }

    @Test
    fun effectiveWorkingDays_overrideWinsOverDaysOff() {
        val month = YearMonth.of(2026, 10)

        assertEquals(15, effectiveWorkingDays(month, 15, 5))
        assertEquals(0, effectiveWorkingDays(month, 0, 5))
    }

    @Test
    fun effectiveWorkingDays_clampsAtZero() {
        val month = YearMonth.of(2026, 10)

        assertEquals(0, effectiveWorkingDays(month, null, 21))
        assertEquals(0, effectiveWorkingDays(month, null, MAX_DAYS_OFF))
        assertEquals(0, effectiveWorkingDays(month, null, 500))
    }
}
