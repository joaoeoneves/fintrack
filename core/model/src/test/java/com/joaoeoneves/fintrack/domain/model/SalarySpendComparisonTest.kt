package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SalarySpendComparisonTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)

    @Test
    fun yearScale_noMonth_isNull() {
        assertNull(salarySpendComparisonOf(null, 100_000, 50_000, oct))
    }

    @Test
    fun nothingReceived_isNull() {
        assertNull(salarySpendComparisonOf(sep, 0, 50_000, oct))
        assertNull(salarySpendComparisonOf(sep, -1, 50_000, oct))
    }

    @Test
    fun followingMonthInTheFuture_isNull() {
        assertNull(salarySpendComparisonOf(oct, 100_000, 0, oct))
        assertNull(salarySpendComparisonOf(YearMonth.of(2026, 11), 100_000, 0, oct))
    }

    @Test
    fun followingMonthIsTheCurrentOne_isPartial() {
        val comparison = salarySpendComparisonOf(sep, 100_000, 40_000, oct)

        assertEquals(SalarySpendComparison(sep, 100_000, oct, 40_000, spentIsPartial = true), comparison)
    }

    @Test
    fun followingMonthInThePast_isComplete() {
        val comparison = salarySpendComparisonOf(YearMonth.of(2026, 7), 100_000, 60_000, oct)

        assertEquals(YearMonth.of(2026, 8), comparison?.spentMonth)
        assertEquals(60_000L, comparison?.spentCents)
        assertFalse(comparison?.spentIsPartial ?: true)
    }

    @Test
    fun december_comparesWithJanuaryOfTheNextYear() {
        val dec = YearMonth.of(2026, 12)

        val partial = salarySpendComparisonOf(dec, 100_000, 10, YearMonth.of(2027, 1))
        val complete = salarySpendComparisonOf(dec, 100_000, 10, YearMonth.of(2027, 3))

        assertEquals(YearMonth.of(2027, 1), partial?.spentMonth)
        assertTrue(partial?.spentIsPartial == true)
        assertFalse(complete?.spentIsPartial ?: true)
        assertNull(salarySpendComparisonOf(dec, 100_000, 10, dec))
    }
}
