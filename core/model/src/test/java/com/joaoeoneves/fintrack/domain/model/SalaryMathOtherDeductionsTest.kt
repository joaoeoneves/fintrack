package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.bonusExtra
import org.junit.Assert.assertEquals
import org.junit.Test

/** ADSE / other deductions: taken from the monthly gross and the subsidy lumps like SS, never from extras. */
class SalaryMathOtherDeductionsTest {
    private fun gross(
        spread: Int = SPREAD_NONE_PPM,
        other: Int = 35_000,
        extras: List<SalaryExtra> = emptyList(),
    ) = SalaryProfile(
        baseGrossCents = 200_000,
        subsidySpreadPpm = spread,
        irsBasePpm = 100_000,
        ssPpm = 110_000,
        otherDeductionsPpm = other,
        extras = extras,
    )

    private fun breakdown(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile))

    @Test
    fun golden_monthlyAndLumpNetAre1510() {
        val b = breakdown(gross())

        assertEquals(7_000, b.otherDeductionsCents)
        assertEquals(151_000, b.netMonthlyCents)
        assertEquals(151_000, b.holidayLumpNetCents)
        assertEquals(151_000, b.christmasLumpNetCents)
    }

    @Test
    fun zeroOtherDeductions_leavesTheOwnerLikeProfileUnchanged() {
        val owner =
            SalaryProfile(
                baseGrossCents = 100_000,
                irsBasePpm = 100_000,
                irsSubsidiesPpm = 50_000,
                ssPpm = 110_000,
            )
        val b = breakdown(owner)

        assertEquals(0, b.otherDeductionsCents)
        assertEquals(86_000, b.netMonthlyCents)
        assertEquals(39_500, b.holidayLumpNetCents)
    }

    @Test
    fun netBasePlusNetDuo_equalsNetMonthly_atEverySpread() {
        for (spread in listOf(SPREAD_NONE_PPM, SPREAD_HALF_PPM, SPREAD_FULL_PPM)) {
            val b = breakdown(gross(spread = spread))

            assertEquals("spread $spread", b.netMonthlyCents, b.netBaseCents + b.netDuoCents)
        }
    }

    @Test
    fun otherDeductions_applyToTheDuodecimosToo() {
        // Full spread: base 2000 + duo 333.33 = 2333.33 gross; 3.5% = 81.67 withheld on all of it.
        val b = breakdown(gross(spread = SPREAD_FULL_PPM))

        assertEquals(233_333, b.grossCents)
        assertEquals(8_167, b.otherDeductionsCents)
        // 2333.33 - 11% - 3.5% - IRS 10% of base 2000 only (subsidy rate defaults to base: 10% of the duo too).
        // Exact: 233333.33 - 25666.67 - 8166.67 - 23333.33 = 176166.67, rounded once.
        assertEquals(176_167, b.netMonthlyCents)
    }

    @Test
    fun extras_areNotAffectedByOtherDeductions() {
        val extra = bonusExtra(cents = 100_000)
        val with = breakdown(gross(other = 35_000, extras = listOf(extra))).extras.map { it.totalCents }
        val without = breakdown(gross(other = 0, extras = listOf(extra))).extras.map { it.totalCents }

        assertEquals(without, with)
    }

    @Test
    fun netMode_hasNoOtherDeductions() {
        val net =
            SalaryProfile(
                inputMode = SalaryInputMode.NET,
                netMonthlyCents = 150_000,
                otherDeductionsPpm = 35_000,
            )
        val b = breakdown(net)

        assertEquals(0, b.otherDeductionsCents)
        assertEquals(150_000, b.netMonthlyCents)
    }
}
