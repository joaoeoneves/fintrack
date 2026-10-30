package com.joaoeoneves.fintrack.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvestmentRatePercentTest {
    @Test
    fun nothingInvested_isNull() {
        assertNull(investmentRatePercent(investedCents = 0L, receivedCents = 100_000L))
        assertNull(investmentRatePercent(investedCents = -500L, receivedCents = 100_000L))
    }

    @Test
    fun nothingReceived_isNull() {
        assertNull(investmentRatePercent(investedCents = 5_000L, receivedCents = 0L))
        assertNull(investmentRatePercent(investedCents = 5_000L, receivedCents = -1L))
    }

    @Test
    fun exactShare_isThePercent() {
        assertEquals(10, investmentRatePercent(investedCents = 10_000L, receivedCents = 100_000L))
        assertEquals(100, investmentRatePercent(investedCents = 7_000L, receivedCents = 7_000L))
    }

    @Test
    fun moreThanReceived_exceedsOneHundred() {
        assertEquals(250, investmentRatePercent(investedCents = 25_000L, receivedCents = 10_000L))
    }

    @Test
    fun rounding_isHalfUp() {
        assertEquals(13, investmentRatePercent(investedCents = 5_000L, receivedCents = 40_000L)) // 12.5
        assertEquals(12, investmentRatePercent(investedCents = 4_999L, receivedCents = 40_000L)) // 12.4975
        assertEquals(1, investmentRatePercent(investedCents = 1L, receivedCents = 200L)) // 0.5
        assertEquals(0, investmentRatePercent(investedCents = 1L, receivedCents = 201L)) // 0.497
    }

    @Test
    fun hugeAmounts_doNotOverflow() {
        assertEquals(50, investmentRatePercent(investedCents = 5_000_000_000_000L, receivedCents = 10_000_000_000_000L))
    }
}
