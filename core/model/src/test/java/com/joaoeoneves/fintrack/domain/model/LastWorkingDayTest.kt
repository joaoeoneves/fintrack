package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [lastWorkingDayOf]: the day pay is expected. */
class LastWorkingDayTest {
    @Test
    fun monthEndingOnWeekday_isItsLastDay() {
        // 30 Oct 2026 is a Friday, 31 a Saturday; 30 Sep 2026 is a Wednesday.
        assertEquals(LocalDate.of(2026, 9, 30), lastWorkingDayOf(YearMonth.of(2026, 9)))
    }

    @Test
    fun monthEndingOnSaturday_fallsBackToFriday() {
        assertEquals(LocalDate.of(2026, 10, 30), lastWorkingDayOf(YearMonth.of(2026, 10)))
    }

    @Test
    fun monthEndingOnSunday_fallsBackToFriday() {
        // 31 Jan 2027 is a Sunday.
        assertEquals(LocalDate.of(2027, 1, 29), lastWorkingDayOf(YearMonth.of(2027, 1)))
    }

    @Test
    fun customHoliday_onTheLastWeekday_movesItBack() {
        val holidays = setOf(LocalDate.of(2027, 1, 29))

        assertEquals(LocalDate.of(2027, 1, 28), lastWorkingDayOf(YearMonth.of(2027, 1), holidays))
    }

    @Test
    fun consecutiveHolidays_skipAllOfThem() {
        val holidays = setOf(LocalDate.of(2027, 1, 29), LocalDate.of(2027, 1, 28))

        assertEquals(LocalDate.of(2027, 1, 27), lastWorkingDayOf(YearMonth.of(2027, 1), holidays))
    }

    @Test
    fun holidayOnAWeekend_changesNothing() {
        val holidays = setOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 30))

        assertEquals(LocalDate.of(2027, 1, 29), lastWorkingDayOf(YearMonth.of(2027, 1), holidays))
    }

    @Test
    fun february_commonYearEndingOnSaturday() {
        // 28 Feb 2026 is a Saturday.
        assertEquals(LocalDate.of(2026, 2, 27), lastWorkingDayOf(YearMonth.of(2026, 2)))
    }

    @Test
    fun february_leapYear_usesThe29th() {
        // 29 Feb 2028 is a Tuesday.
        assertEquals(LocalDate.of(2028, 2, 29), lastWorkingDayOf(YearMonth.of(2028, 2)))
    }

    @Test
    fun nationalHolidayOnTheLastWeekday_isSkippedByDefault() {
        // 25 Dec 2026 is a Friday and a holiday, but the month's last weekday is Thu 31 Dec: unaffected.
        assertEquals(LocalDate.of(2026, 12, 31), lastWorkingDayOf(YearMonth.of(2026, 12)))
        // 31 Dec as a custom holiday moves it to Wed 30.
        assertEquals(
            LocalDate.of(2026, 12, 30),
            lastWorkingDayOf(YearMonth.of(2026, 12), setOf(LocalDate.of(2026, 12, 31))),
        )
    }
}
