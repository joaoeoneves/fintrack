package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flexible-benefit style extra: a percent of the annual gross, a fee, and its quarterly split. */
class SalaryMathFlexTest {
    private fun flex(
        base: Long = 100_000,
        ppm: Long = 70_000,
        fee: Int = 0,
    ) = SalaryProfile(baseGrossCents = base, extras = listOf(testExtra(amount = ppm, feePpm = fee)))

    private fun extra(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile)).extras.single()

    private fun quarters(profile: SalaryProfile) = extra(profile).payments.map { it.second }

    @Test
    fun annual_isFourteenSalariesTimesPercent() {
        val e = extra(flex())

        assertEquals(98_000, e.totalCents)
        assertEquals(listOf(24_500L, 24_500L, 24_500L, 24_500L), quarters(flex()))
        assertEquals(listOf(1, 4, 7, 10), e.payments.map { it.first })
    }

    @Test
    fun withdrawalFee_reducesTheNetAnnual() {
        val e = extra(flex(fee = 100_000))

        assertEquals(88_200, e.totalCents)
        assertEquals(22_050L, e.payments.first().second)
    }

    @Test
    fun remainder_landsOnTheLastQuarter() {
        // 100001 * 14 * 7% = 98000.98 -> 98001
        val profile = flex(base = 100_001)

        assertEquals(98_001, extra(profile).totalCents)
        assertEquals(listOf(24_500L, 24_500L, 24_500L, 24_501L), quarters(profile))
    }

    @Test
    fun remainderOfThree_landsOnTheLastQuarter() {
        // 98000 with a 0.5% fee = 97510 -> 24377 x3 + 24379
        val profile = flex(fee = 5_000)

        assertEquals(97_510, extra(profile).totalCents)
        assertEquals(listOf(24_377L, 24_377L, 24_377L, 24_379L), quarters(profile))
    }

    @Test
    fun noPayingExtra_hasNoBreakdownEntry() {
        assertTrue(requireNotNull(salaryBreakdownOf(SalaryProfile(baseGrossCents = 100_000))).extras.isEmpty())
    }

    @Test
    fun lines_followTheExtraMonths() {
        val lines = salaryProfileLines(flex(), emptyList()).filter { it.id.startsWith("profile_extra_flex_") }

        assertEquals(listOf(1, 4, 7, 10), lines.map { it.monthOfYear })
    }
}
