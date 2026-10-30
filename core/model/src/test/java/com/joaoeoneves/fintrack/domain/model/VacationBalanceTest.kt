package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

class VacationBalanceTest {
    private fun ym(
        year: Int,
        month: Int,
    ) = YearMonth.of(year, month)

    @Test
    fun countsOnlyVacationDaysOfTheYear_notSickOrOtherNorOtherYears() {
        val settings =
            listOf(
                MonthSettings(ym(2026, 1), vacationDays = 5, sickDays = 3, otherDays = 2),
                MonthSettings(ym(2026, 12), vacationDays = 4),
                MonthSettings(ym(2025, 12), vacationDays = 9),
                MonthSettings(ym(2027, 1), vacationDays = 7),
            )
        val balance = vacationBalanceOf(2026, settings, SalaryProfile(vacationDaysPerYear = 25))
        assertEquals(VacationBalance(used = 9, total = 25), balance)
        assertEquals(16, balance.remaining)
        assertEquals(0, balance.overBy)
    }

    @Test
    fun nullProfileAssumesTheDefault22() {
        val balance = vacationBalanceOf(2026, emptyList(), null)
        assertEquals(22, balance.total)
        assertEquals(22, balance.remaining)
    }

    @Test
    fun overUseClampsRemainingToZeroAndReportsTheExcess() {
        val settings =
            listOf(MonthSettings(ym(2026, 8), vacationDays = 15), MonthSettings(ym(2026, 9), vacationDays = 10))
        val balance = vacationBalanceOf(2026, settings, SalaryProfile(vacationDaysPerYear = 22))
        assertEquals(0, balance.remaining)
        assertEquals(3, balance.overBy)
    }

    @Test
    fun requireValidDaysOff_acceptsTheBoundaries() {
        requireValidDaysOff(listOf(ym(2026, 1)), 0, 0, 0)
        requireValidDaysOff(listOf(ym(2026, 1)), MAX_DAYS_OFF, 0, 0)
        val months = (0 until MAX_DAYS_OFF_MONTHS).map { ym(2026, 1).plusMonths(it.toLong()) }
        requireValidDaysOff(months, 10, 8, 5)
    }

    @Test
    fun requireValidDaysOff_rejectsBadMonthCountsAndCounts() {
        val one = listOf(ym(2026, 1))
        assertThrows(IllegalArgumentException::class.java) { requireValidDaysOff(emptyList(), 1, 0, 0) }
        val tooMany = (0..MAX_DAYS_OFF_MONTHS).map { ym(2026, 1).plusMonths(it.toLong()) }
        assertThrows(IllegalArgumentException::class.java) { requireValidDaysOff(tooMany, 1, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { requireValidDaysOff(one, -1, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { requireValidDaysOff(one, 0, MAX_DAYS_OFF + 1, 0) }
        // Total above the cap even though each count is valid on its own.
        assertThrows(IllegalArgumentException::class.java) { requireValidDaysOff(one, 10, 10, MAX_DAYS_OFF - 19) }
    }

    @Test
    fun daysOffIsTheTotal_andBlankOnlyWhenAllZero() {
        assertEquals(6, MonthSettings(ym(2026, 1), 1, 2, 3).daysOff)
        assertEquals(true, MonthSettings(ym(2026, 1)).isBlank)
        assertEquals(false, MonthSettings(ym(2026, 1), sickDays = 1).isBlank)
    }

    @Test
    fun defaultEffectiveFrom_offUsesNow() {
        assertEquals(ym(2026, 10), defaultEffectiveFrom(ym(2026, 10), false))
        assertEquals(ym(2026, 1), defaultEffectiveFrom(ym(2026, 1), false))
    }

    @Test
    fun defaultEffectiveFrom_februaryRaiseOfTheCurrentYear() {
        assertEquals(ym(2026, 2), defaultEffectiveFrom(ym(2026, 1), true))
        assertEquals(ym(2026, 2), defaultEffectiveFrom(ym(2026, 2), true))
        // Documented behaviour: February..December keep this year's February.
        assertEquals(ym(2026, 2), defaultEffectiveFrom(ym(2026, 12), true))
    }
}
