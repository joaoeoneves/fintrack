package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class PlannedIncomeScheduleTest {
    private val oct = YearMonth.of(2026, 10)

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun line(
        amount: Long = 1046L,
        basis: IncomeBasis = IncomeBasis.FIXED,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
        monthOfYear: Int? = null,
        yearMonth: YearMonth? = null,
        skipMonths: Set<Int> = emptySet(),
    ) = PlannedIncome("l", "Line", amount, 0, basis, frequency, monthOfYear, yearMonth, skipMonths)

    @Test
    fun perWorkingDay_multipliesRateByDays() {
        assertEquals(23_012L, line(basis = IncomeBasis.PER_WORKING_DAY).amountIn(oct, 22))
    }

    @Test
    fun perWorkingDay_zeroDays_isZero() {
        assertEquals(0L, line(basis = IncomeBasis.PER_WORKING_DAY).amountIn(oct, 0))
    }

    @Test
    fun fixed_ignoresWorkingDays() {
        assertEquals(1046L, line().amountIn(oct, 22))
        assertEquals(1046L, line().amountIn(oct, 0))
    }

    @Test
    fun monthly_skippedMonth_isZeroAndInactive() {
        val l = line(skipMonths = setOf(10))

        assertEquals(0L, l.amountIn(oct, 22))
        assertFalse(l.isActiveIn(oct))
        assertTrue(l.isActiveIn(YearMonth.of(2026, 11)))
    }

    @Test
    fun monthly_skippedMonth_appliesEveryYear() {
        val l = line(skipMonths = setOf(8))

        assertFalse(l.isActiveIn(YearMonth.of(2026, 8)))
        assertFalse(l.isActiveIn(YearMonth.of(2031, 8)))
    }

    @Test
    fun perWorkingDay_skippedMonth_isZero() {
        assertEquals(0L, line(basis = IncomeBasis.PER_WORKING_DAY, skipMonths = setOf(10)).amountIn(oct, 22))
    }

    @Test
    fun yearly_countsOnlyInItsMonth_ofAnyYear() {
        val l = line(amount = 90_000L, frequency = IncomeFrequency.YEARLY, monthOfYear = 7)

        assertEquals(90_000L, l.amountIn(YearMonth.of(2026, 7), 22))
        assertEquals(90_000L, l.amountIn(YearMonth.of(2027, 7), 22))
        assertEquals(0L, l.amountIn(YearMonth.of(2026, 6), 22))
        assertEquals(0L, l.amountIn(YearMonth.of(2026, 8), 22))
    }

    @Test
    fun once_countsOnlyInItsYearMonth() {
        val l = line(amount = 50_000L, frequency = IncomeFrequency.ONCE, yearMonth = YearMonth.of(2026, 12))

        assertEquals(50_000L, l.amountIn(YearMonth.of(2026, 12), 22))
        assertEquals(0L, l.amountIn(YearMonth.of(2027, 12), 22))
        assertEquals(0L, l.amountIn(YearMonth.of(2026, 11), 22))
    }

    @Test
    fun yearly_perWorkingDay_multipliesWhenActive() {
        val l =
            line(
                basis = IncomeBasis.PER_WORKING_DAY,
                frequency = IncomeFrequency.YEARLY,
                monthOfYear = 10,
            )

        assertEquals(23_012L, l.amountIn(oct, 22))
    }

    @Test
    fun isExtra_onlyForYearlyAndOnce() {
        assertFalse(line().isExtra)
        assertTrue(line(frequency = IncomeFrequency.YEARLY, monthOfYear = 1).isExtra)
        assertTrue(line(frequency = IncomeFrequency.ONCE, yearMonth = oct).isExtra)
    }

    @Test
    fun defaults_areFixedMonthly() {
        val l = PlannedIncome("a", "b", 1, 0)

        assertEquals(IncomeBasis.FIXED, l.basis)
        assertEquals(IncomeFrequency.MONTHLY, l.frequency)
        assertTrue(l.skipMonths.isEmpty())
    }
}
