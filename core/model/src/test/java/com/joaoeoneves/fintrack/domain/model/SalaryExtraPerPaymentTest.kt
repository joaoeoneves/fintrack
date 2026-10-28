package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SalaryExtraAmountScope.PER_PAYMENT]: the amount is paid in every selected month, TOTAL is unchanged. */
class SalaryExtraPerPaymentTest {
    // base 1000.00, IRS 10%, SS 11%: IRS factor 0.90, IRS+SS factor 0.79.
    private val gross =
        SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, irsSubsidiesPpm = 400_000)
    private val net = SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 100_000)
    private val perPayment = SalaryExtraAmountScope.PER_PAYMENT
    private val fixed = SalaryExtraAmountType.FIXED_CENTS

    private fun breakdown(
        extra: SalaryExtra,
        profile: SalaryProfile = gross,
    ) = requireNotNull(salaryBreakdownOf(profile.copy(extras = listOf(extra)))).extras.singleOrNull()

    @Test
    fun fixed_isPaidInEveryMonth() {
        val b = breakdown(testExtra(type = fixed, amount = 50_000, months = setOf(6, 3, 9), amountScope = perPayment))

        assertEquals(listOf(3 to 50_000L, 6 to 50_000L, 9 to 50_000L), b?.payments)
        assertEquals(150_000L, b?.totalCents)
    }

    @Test
    fun multiple_withDeductions_isTaxedPerPayment() {
        val extra =
            testExtra(
                type = SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM,
                amount = 2_500_000,
                months = setOf(4, 10),
                deductions = SalaryExtraDeductions.IRS,
                amountScope = perPayment,
            )

        val b = breakdown(extra)

        assertEquals(listOf(4 to 225_000L, 10 to 225_000L), b?.payments)
        assertEquals(450_000L, b?.totalCents)
    }

    @Test
    fun percent_withDeductionsAndFee_perPayment() {
        // 7% of 14 bases = 980.00, x 0.79 = 774.20, x (1 - 10%) = 696.78
        val extra =
            testExtra(
                amount = 70_000,
                months = setOf(1, 4, 7, 10),
                deductions = SalaryExtraDeductions.IRS_SS,
                feePpm = 100_000,
                amountScope = perPayment,
            )

        val b = breakdown(extra)

        assertEquals(List(4) { 69_678L }, b?.payments?.map { it.second })
        assertEquals(278_712L, b?.totalCents)
    }

    @Test
    fun roundsOncePerPayment_soPerTimesCountDiffersFromTheRoundedTotal() {
        // 10.01 x 0.79 = 7.9079 -> 7.91 per payment; as a yearly total 30.03 x 0.79 = 23.7237 -> 23.72.
        val per =
            testExtra(
                type = fixed,
                amount = 1_001,
                months = setOf(1, 2, 3),
                deductions = SalaryExtraDeductions.IRS_SS,
                amountScope = perPayment,
            )
        val total = per.copy(amount = 3_003, amountScope = SalaryExtraAmountScope.TOTAL)

        assertEquals(listOf(791L, 791L, 791L), breakdown(per)?.payments?.map { it.second })
        assertEquals(2_373L, breakdown(per)?.totalCents)
        assertEquals(2_372L, breakdown(total)?.totalCents)
        assertEquals(listOf(790L, 790L, 792L), breakdown(total)?.payments?.map { it.second })
    }

    @Test
    fun oneMonth_equalsTotal_forEveryType() {
        for (type in SalaryExtraAmountType.entries) {
            val total =
                testExtra(
                    type = type,
                    amount = 123_457,
                    months = setOf(5),
                    deductions = SalaryExtraDeductions.IRS_SS,
                    feePpm = 33_333,
                )

            assertEquals(type.name, breakdown(total), breakdown(total.copy(amountScope = perPayment)))
        }
    }

    @Test
    fun totalScope_isTheDefault_andUnchanged() {
        val explicit = testExtra(type = fixed, amount = 100_000, months = setOf(1, 2, 3))

        assertEquals(SalaryExtraAmountScope.TOTAL, explicit.amountScope)
        assertEquals(listOf(33_333L, 33_333L, 33_334L), breakdown(explicit)?.payments?.map { it.second })
    }

    @Test
    fun net_fixedIsPerPayment_otherTypesSkipped() {
        val fixedExtra = testExtra(type = fixed, amount = 50_000, months = setOf(3, 6), amountScope = perPayment)
        val percent = testExtra(id = "p", months = setOf(3, 6), amountScope = perPayment)
        val multipleType = SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM
        val multiple = testExtra(id = "m", type = multipleType, months = setOf(3), amountScope = perPayment)

        assertEquals(100_000L, breakdown(fixedExtra, net)?.totalCents)
        assertEquals(listOf(50_000L, 50_000L), breakdown(fixedExtra, net)?.payments?.map { it.second })
        assertNull(breakdown(percent, net))
        assertNull(breakdown(multiple, net))
    }

    @Test
    fun fullFee_pays_nothing_andIsOmitted() {
        val extra =
            testExtra(type = fixed, amount = 50_000, months = setOf(1, 2), feePpm = PPM_SCALE, amountScope = perPayment)

        assertNull(breakdown(extra))
    }

    @Test
    fun noMonths_isOmitted() {
        assertNull(breakdown(testExtra(type = fixed, amount = 50_000, months = emptySet(), amountScope = perPayment)))
    }

    @Test
    fun lines_oneManagedLinePerMonth_equalAmounts_andSyncIsIdempotent() {
        val extra = testExtra(type = fixed, amount = 50_000, months = setOf(2, 8, 11), amountScope = perPayment)
        val profile = gross.copy(extras = listOf(extra))

        val lines = salaryProfileLines(profile, emptyList())
        val extraLines = lines.filter { parseManagedExtraKey(it.managedKey.orEmpty()) != null }

        assertEquals(listOf(2, 8, 11), extraLines.map { it.monthOfYear })
        assertTrue(extraLines.all { it.amountCents == 50_000L })
        assertEquals(extraLines.map { it.id }, extraLines.map { it.managedKey })
        assertEquals(lines, salaryProfileLines(profile, lines))
        assertTrue(managedLinesSync(profile, lines).isEmpty)
    }
}
