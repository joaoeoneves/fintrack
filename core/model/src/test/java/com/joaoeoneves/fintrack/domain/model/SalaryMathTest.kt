package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gross-mode salary math with neutral figures: base 1000.00, SS 11%, IRS 10% on the base and 5% on subsidies. */
class SalaryMathTest {
    private fun gross(
        base: Long? = 100_000,
        spread: Int = SPREAD_HALF_PPM,
        exempt: Int = 0,
    ) = SalaryProfile(
        baseGrossCents = base,
        subsidySpreadPpm = spread,
        irsBasePpm = 100_000,
        irsSubsidiesPpm = 50_000,
        ssPpm = 110_000,
        irsJovemExemptPpm = exempt,
    )

    private fun breakdown(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile))

    @Test
    fun halfSpread_goldenValues() {
        val b = breakdown(gross())

        assertEquals(100_000, b.baseCents)
        assertEquals(8_333, b.duoCents)
        assertEquals(108_333, b.grossCents)
        assertEquals(11_917, b.ssCents)
        assertEquals(10_000, b.irsBaseCents)
        assertEquals(417, b.irsSubsidiesCents)
        assertEquals(10_417, b.irsCents)
        // Exact: 108333.33 - 11916.67 - 10416.67 = 86000.00; deriving it from the rounded parts would give 85999.
        assertEquals(86_000, b.netMonthlyCents)
        assertEquals(50_000, b.lumpGrossCents)
        // The lump is taxed at the base rate: 50000 * (1 - 0.11 - 0.10).
        assertEquals(39_500, b.holidayLumpNetCents)
        assertEquals(39_500, b.christmasLumpNetCents)
    }

    @Test
    fun netIsRoundedOnceFromExactValues_notFromRoundedComponents() {
        val b = breakdown(gross())

        // The displayed components do not add up to the net: that is deliberate (a single rounding).
        assertEquals(85_999, b.grossCents - b.ssCents - b.irsCents)
        assertEquals(86_000, b.netMonthlyCents)
    }

    @Test
    fun noSpread_hasNoDuoAndLumpIsTheWholeBase() {
        val b = breakdown(gross(spread = SPREAD_NONE_PPM))

        assertEquals(0, b.duoCents)
        assertEquals(100_000, b.grossCents)
        assertEquals(11_000, b.ssCents)
        assertEquals(10_000, b.irsCents)
        assertEquals(79_000, b.netMonthlyCents)
        assertEquals(100_000, b.lumpGrossCents)
        assertEquals(79_000, b.holidayLumpNetCents)
    }

    @Test
    fun fullSpread_hasNoLumps() {
        val b = breakdown(gross(spread = SPREAD_FULL_PPM))

        assertEquals(16_667, b.duoCents)
        assertEquals(116_667, b.grossCents)
        assertEquals(93_000, b.netMonthlyCents)
        assertEquals(0, b.lumpGrossCents)
        assertEquals(0, b.holidayLumpNetCents)
        assertEquals(0, b.christmasLumpNetCents)
    }

    @Test
    fun irsJovemExemption_reducesIrsProportionally() {
        val half = breakdown(gross(exempt = 500_000))
        val full = breakdown(gross(exempt = PPM_SCALE))

        assertEquals(5_208, half.irsCents)
        assertEquals(91_208, half.netMonthlyCents)
        assertEquals(0, full.irsCents)
        assertEquals(96_417, full.netMonthlyCents)
        // The lump's IRS is exempt too: 50000 * (1 - 0.11).
        assertEquals(44_500, full.holidayLumpNetCents)
    }

    @Test
    fun subsidiesIrsDefaultsToBaseRate() {
        val profile = gross().copy(irsSubsidiesPpm = null)

        // IRS = 10% of (base + duo) = 10833.33; net = 108333.33 - 11916.67 - 10833.33 = 85583.33
        assertEquals(85_583, breakdown(profile).netMonthlyCents)
    }

    @Test
    fun zeroRates_netEqualsGross() {
        val profile = SalaryProfile(baseGrossCents = 100_000, ssPpm = 0, subsidySpreadPpm = SPREAD_NONE_PPM)

        assertEquals(100_000, breakdown(profile).netMonthlyCents)
    }

    @Test
    fun missingOrZeroBase_isNull() {
        assertNull(salaryBreakdownOf(gross(base = null)))
        assertNull(salaryBreakdownOf(gross(base = 0)))
        assertNull(salaryBreakdownOf(gross(base = -5)))
    }

    @Test
    fun smallBase_roundsHalfUp() {
        // 1 cent: duo 0.0833, nothing reaches a whole cent but the net rounds to 1.
        val b = breakdown(SalaryProfile(baseGrossCents = 1, ssPpm = 0))

        assertEquals(0, b.duoCents)
        assertEquals(1, b.netMonthlyCents)
    }

    @Test
    fun multipleExtra_isBaseTimesMultiplier() {
        val b = breakdown(gross().copy(extras = listOf(multipleExtra(ppm = 1_500_000, months = setOf(3)))))

        assertEquals(150_000, b.extras.single().totalCents)
        assertTrue(breakdown(gross()).extras.isEmpty())
    }

    @Test
    fun meal_passesThrough() {
        assertEquals(900, breakdown(gross().copy(mealRatePerDayCents = 900)).mealRatePerDayCents)
    }

    @Test
    fun netMode_usesTheTypedAmounts() {
        val profile =
            SalaryProfile(
                inputMode = SalaryInputMode.NET,
                netMonthlyCents = 120_000,
                netHolidayLumpCents = 60_000,
                netChristmasLumpCents = 61_000,
                extras = listOf(testExtra(type = SalaryExtraAmountType.FIXED_CENTS, amount = 100_001)),
                // Gross-only fields are ignored in NET mode.
                baseGrossCents = 999_999,
            )

        val b = breakdown(profile)

        assertEquals(120_000, b.netMonthlyCents)
        assertEquals(60_000, b.holidayLumpNetCents)
        assertEquals(61_000, b.christmasLumpNetCents)
        assertEquals(
            listOf(25_000L, 25_000L, 25_000L, 25_001L),
            b.extras
                .single()
                .payments
                .map { it.second },
        )
        assertEquals(0, b.grossCents)
    }

    @Test
    fun netMode_missingNet_isNull() {
        assertNull(salaryBreakdownOf(SalaryProfile(inputMode = SalaryInputMode.NET)))
        assertNull(salaryBreakdownOf(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 0)))
        assertNotNull(salaryBreakdownOf(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 1)))
    }

    @Test
    fun netMode_withoutOptionalAmounts_hasZeros() {
        val b = breakdown(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 50_000))

        assertEquals(0, b.holidayLumpNetCents)
        assertTrue(b.extras.isEmpty())
    }
}
