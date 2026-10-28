package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The monthly net is split into the base salary alone and the subsidy twelfths, adding up exactly. */
class SalaryMathNetSplitTest {
    private fun gross(
        base: Long? = 100_000,
        spread: Int = SPREAD_HALF_PPM,
        exempt: Int = 0,
        ss: Int = 110_000,
        irs: Int = 100_000,
    ) = SalaryProfile(
        baseGrossCents = base,
        subsidySpreadPpm = spread,
        irsBasePpm = irs,
        irsSubsidiesPpm = 50_000,
        ssPpm = ss,
        irsJovemExemptPpm = exempt,
    )

    private fun breakdown(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile))

    @Test
    fun split_addsUpToTheNet_forEverySpread() {
        for (spread in SUBSIDY_SPREADS_PPM) {
            for (exempt in listOf(0, 500_000, 1_000_000)) {
                val b = breakdown(gross(spread = spread, exempt = exempt))

                assertEquals("spread $spread exempt $exempt", b.netMonthlyCents, b.netBaseCents + b.netDuoCents)
            }
        }
    }

    @Test
    fun halfSpread_goldenSplit() {
        val b = breakdown(gross())

        assertEquals(86_000, b.netMonthlyCents)
        assertEquals(79_000, b.netBaseCents)
        assertEquals(7_000, b.netDuoCents)
    }

    @Test
    fun fullSpread_goldenSplit() {
        val b = breakdown(gross(spread = SPREAD_FULL_PPM))

        assertEquals(93_000, b.netMonthlyCents)
        assertEquals(79_000, b.netBaseCents)
        assertEquals(14_000, b.netDuoCents)
    }

    @Test
    fun noSpread_hasNoDuo_andBaseIsTheNet() {
        val b = breakdown(gross(spread = SPREAD_NONE_PPM))

        assertEquals(0, b.netDuoCents)
        assertEquals(b.netMonthlyCents, b.netBaseCents)
        assertEquals(79_000, b.netBaseCents)
    }

    @Test
    fun irsJovem_reducesTheBaseIrs() {
        val b = breakdown(gross(exempt = 500_000))

        // 100000 - 11000 - 10000 * 50%
        assertEquals(84_000, b.netBaseCents)
        assertEquals(91_208, b.netMonthlyCents)
        assertEquals(7_208, b.netDuoCents)
    }

    @Test
    fun roundingEdge_baseRoundsOnceHalfUp_duoIsTheRemainder() {
        // Net base is exactly 500.5 cents: HALF_UP gives 501, and with no spread the duo is 0.
        val half = breakdown(gross(base = 1_001, spread = SPREAD_NONE_PPM, ss = 500_000, irs = 0))
        assertEquals(501, half.netBaseCents)
        assertEquals(0, half.netDuoCents)

        // 5 cents + 0.83 of twelfths = 5.83 -> 6 in total: the duo absorbs the rounding.
        val tiny = breakdown(gross(base = 5, spread = SPREAD_FULL_PPM, ss = 0, irs = 0))
        assertEquals(6, tiny.netMonthlyCents)
        assertEquals(5, tiny.netBaseCents)
        assertEquals(1, tiny.netDuoCents)
    }

    @Test
    fun netMode_hasNoSplit() {
        val b = breakdown(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 120_000))

        assertEquals(120_000, b.netBaseCents)
        assertEquals(0, b.netDuoCents)
        assertEquals(120_000, b.netMonthlyCents)
    }
}
