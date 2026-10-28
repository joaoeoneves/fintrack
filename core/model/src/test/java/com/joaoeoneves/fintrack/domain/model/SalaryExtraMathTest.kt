package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-type, per-deduction, fee, rounding, split and NET behaviour of [SalaryExtra]. */
class SalaryExtraMathTest {
    // base 1000.00, IRS 10%, SS 11%, no exemption: IRS factor 0.90, IRS+SS factor 0.79.
    private fun gross(vararg extras: SalaryExtra) =
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            irsSubsidiesPpm = 400_000,
            extras = extras.toList(),
        )

    private fun net(vararg extras: SalaryExtra) =
        SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 100_000, extras = extras.toList())

    private fun totals(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile)).extras.map { it.totalCents }

    private fun total(extra: SalaryExtra) = totals(gross(extra)).singleOrNull()

    private val percent = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM
    private val fixed = SalaryExtraAmountType.FIXED_CENTS
    private val multiple = SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM

    @Test
    fun fixed_isTheAmount() {
        assertEquals(50_000L, total(testExtra(type = fixed, amount = 50_000)))
    }

    @Test
    fun multiple_isBaseTimesPpm() {
        assertEquals(250_000L, total(testExtra(type = multiple, amount = 2_500_000)))
    }

    @Test
    fun percent_isFourteenBasesTimesPpm() {
        assertEquals(98_000L, total(testExtra(type = percent, amount = 70_000)))
    }

    @Test
    fun deductions_irsUsesTheBaseRate_neverTheSubsidyRate() {
        val e = testExtra(type = fixed, amount = 100_000, deductions = SalaryExtraDeductions.IRS)

        assertEquals(90_000L, total(e))
    }

    @Test
    fun deductions_irsAndSs() {
        val e = testExtra(type = fixed, amount = 100_000, deductions = SalaryExtraDeductions.IRS_SS)

        assertEquals(79_000L, total(e))
    }

    @Test
    fun deductions_irsJovemExemptionApplies() {
        val p = gross(testExtra(type = fixed, amount = 100_000, deductions = SalaryExtraDeductions.IRS))

        // 50% exempt: IRS paid 5%.
        assertEquals(listOf(95_000L), totals(p.copy(irsJovemExemptPpm = 500_000)))
        assertEquals(listOf(100_000L), totals(p.copy(irsJovemExemptPpm = PPM_SCALE)))
    }

    @Test
    fun fee_isTakenAfterTheDeductions() {
        val e = testExtra(type = fixed, amount = 100_000, deductions = SalaryExtraDeductions.IRS_SS, feePpm = 100_000)

        assertEquals(71_100L, total(e))
    }

    @Test
    fun fee_ofOneHundredPercent_dropsTheExtra() {
        assertNull(total(testExtra(type = fixed, amount = 100_000, feePpm = PPM_SCALE)))
    }

    @Test
    fun rounding_isOneHalfUpAtTheEnd() {
        // 1001 cents * 0.79 = 790.79 -> 791 (rounding the factors earlier would differ)
        val e = testExtra(type = fixed, amount = 1_001, deductions = SalaryExtraDeductions.IRS_SS)
        // 5 * 0.9 = 4.5 -> 5 (HALF_UP)
        val half = testExtra(id = "h", type = fixed, amount = 5, deductions = SalaryExtraDeductions.IRS)

        assertEquals(791L, total(e))
        assertEquals(5L, total(half))
    }

    @Test
    fun split_floorOnEarlierMonths_remainderOnTheLast_ascending() {
        val e = testExtra(type = fixed, amount = 100_000, months = setOf(9, 3, 6))

        val payments = requireNotNull(salaryBreakdownOf(gross(e))).extras.single().payments

        assertEquals(listOf(3 to 33_333L, 6 to 33_333L, 9 to 33_334L), payments)
    }

    @Test
    fun zeroTotal_isDropped_andNoLinesAreProduced() {
        val zero = testExtra(type = fixed, amount = 0)
        val profile = gross(zero, fixedExtra())

        assertEquals(listOf(10_000L), totals(profile))
        val keys = salaryProfileLines(profile, emptyList()).mapNotNull { it.managedKey }
        assertTrue(keys.none { it.startsWith("profile_extra_flex_") })
    }

    @Test
    fun noMonths_pays_nothing() {
        assertNull(total(testExtra(type = fixed, amount = 100, months = emptySet())))
    }

    @Test
    fun extras_keepTheirProfileOrder() {
        val b = requireNotNull(salaryBreakdownOf(gross(fixedExtra(), testExtra(), multipleExtra())))

        assertEquals(listOf("bonus", "flex", "profit"), b.extras.map { it.extraId })
        assertEquals(listOf("Bonus", "Flexible benefits", "Profit share"), b.extras.map { it.name })
    }

    @Test
    fun netMode_onlyFixedCounts_deductionsAndFeeIgnored() {
        val fixedExtra =
            testExtra(
                id = "f",
                type = fixed,
                amount = 100_000,
                deductions = SalaryExtraDeductions.IRS_SS,
                feePpm = 500_000,
            )
        val pct = testExtra(id = "p", type = percent)
        val mul = testExtra(id = "m", type = multiple)

        val b = requireNotNull(salaryBreakdownOf(net(fixedExtra, pct, mul)))

        assertEquals(listOf("f"), b.extras.map { it.extraId })
        assertEquals(100_000L, b.extras.single().totalCents)
        val keys = salaryProfileLines(net(fixedExtra, pct, mul), emptyList()).mapNotNull { it.managedKey }
        assertTrue(keys.none { it.startsWith("profile_extra_p_") || it.startsWith("profile_extra_m_") })
    }

    @Test
    fun netMode_nonFixedExtrasStayStoredOnTheProfile() {
        val p = net(testExtra(type = percent))

        assertEquals(1, p.extras.size)
        assertTrue(requireNotNull(salaryBreakdownOf(p)).extras.isEmpty())
    }

    @Test
    fun grossProfile_withoutBase_hasNoBreakdown() {
        assertNull(salaryBreakdownOf(SalaryProfile(extras = listOf(fixedExtra()))))
    }
}
