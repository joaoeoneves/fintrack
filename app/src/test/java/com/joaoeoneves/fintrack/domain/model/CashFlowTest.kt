package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CashFlowTest {
    private val delta = 1e-9

    @Test
    fun savingsRate_zeroIncome_isNull() {
        assertNull(savingsRate(savedCents = 0L, incomeCents = 0L))
        assertNull(savingsRate(savedCents = 5_000L, incomeCents = 0L))
    }

    @Test
    fun savingsRate_negativeIncome_isNull() {
        assertNull(savingsRate(savedCents = 1_000L, incomeCents = -1L))
    }

    @Test
    fun savingsRate_normal_isFraction() {
        assertEquals(0.25, savingsRate(savedCents = 25_000L, incomeCents = 100_000L)!!, delta)
    }

    @Test
    fun savingsRate_nothingSaved_isZero() {
        assertEquals(0.0, savingsRate(savedCents = 0L, incomeCents = 100_000L)!!, delta)
    }

    @Test
    fun savingsRate_savedMoreThanIncome_exceedsOne() {
        assertEquals(1.5, savingsRate(savedCents = 150_000L, incomeCents = 100_000L)!!, delta)
    }

    @Test
    fun savingsRate_oneCentIncome_isFinite() {
        assertEquals(1.0, savingsRate(savedCents = 1L, incomeCents = 1L)!!, delta)
    }
}
