package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The flexible-benefit annual amount, its quarterly split and the payment months. */
class SalaryMathFlexTest {
    private fun flex(
        base: Long = 100_000,
        flexPpm: Int? = 70_000,
        fee: Int = 0,
        first: Int = 1,
    ) = SalaryProfile(
        baseGrossCents = base,
        flexPpm = flexPpm,
        flexWithdrawalFeePpm = fee,
        flexFirstMonth = first,
    )

    private fun breakdown(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile))

    private fun months(profile: SalaryProfile) =
        salaryProfileLines(profile, emptyList()).filter { it.id.startsWith("profile_flex_q") }.map { it.monthOfYear }

    @Test
    fun annual_isFourteenSalariesTimesPercent() {
        val b = breakdown(flex())

        assertEquals(98_000, b.flexGrossAnnualCents)
        assertEquals(98_000, b.flexAnnualCents)
        assertEquals(listOf(24_500L, 24_500L, 24_500L, 24_500L), b.flexQuarterlyCents)
    }

    @Test
    fun withdrawalFee_reducesTheNetAnnual() {
        val b = breakdown(flex(fee = 100_000))

        assertEquals(98_000, b.flexGrossAnnualCents)
        assertEquals(88_200, b.flexAnnualCents)
        assertEquals(22_050L, b.flexQuarterlyCents.first())
    }

    @Test
    fun remainder_landsOnTheLastQuarter() {
        // 100001 * 14 * 7% = 98000.98 -> 98001
        val b = breakdown(flex(base = 100_001))

        assertEquals(98_001, b.flexAnnualCents)
        assertEquals(listOf(24_500L, 24_500L, 24_500L, 24_501L), b.flexQuarterlyCents)
        assertEquals(b.flexAnnualCents, b.flexQuarterlyCents.sum())
    }

    @Test
    fun remainderOfThree_landsOnTheLastQuarter() {
        // 98000 with a 0.5% fee = 97510 -> 24377 x3 + 24379
        val b = breakdown(flex(fee = 5_000))

        assertEquals(97_510, b.flexAnnualCents)
        assertEquals(listOf(24_377L, 24_377L, 24_377L, 24_379L), b.flexQuarterlyCents)
    }

    @Test
    fun noFlex_isAllZero() {
        val b = breakdown(flex(flexPpm = null))

        assertEquals(0, b.flexAnnualCents)
        assertEquals(listOf(0L, 0L, 0L, 0L), b.flexQuarterlyCents)
    }

    @Test
    fun months_fromJanuary() {
        assertEquals(listOf(1, 4, 7, 10), months(flex(first = 1)))
    }

    @Test
    fun months_fromMarch() {
        assertEquals(listOf(3, 6, 9, 12), months(flex(first = 3)))
    }

    @Test
    fun months_wrapPastDecember() {
        assertEquals(listOf(11, 2, 5, 8), months(flex(first = 11)))
        assertEquals(listOf(12, 3, 6, 9), months(flex(first = 12)))
    }
}
