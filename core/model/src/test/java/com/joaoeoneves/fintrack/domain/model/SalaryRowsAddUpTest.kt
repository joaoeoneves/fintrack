package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The salary breakdown rows add up to the cent for every profile shape, whatever the rounding. */
class SalaryRowsAddUpTest {
    private fun profile(
        base: Long,
        spread: Int = SPREAD_HALF_PPM,
        irs: Int = 123_457,
        jovem: Int = 0,
        other: Int = 0,
    ) = SalaryProfile(
        baseGrossCents = base,
        subsidySpreadPpm = spread,
        irsBasePpm = irs,
        irsSubsidiesPpm = 50_001,
        irsJovemExemptPpm = jovem,
        otherDeductionsPpm = other,
    )

    private val profiles =
        listOf(
            profile(100_000),
            profile(123_457, SPREAD_FULL_PPM),
            profile(123_457, SPREAD_NONE_PPM),
            profile(99_999, jovem = 500_000),
            profile(187_333, SPREAD_FULL_PPM, jovem = 250_000, other = 35_000),
            profile(100_001, other = 17_777),
            profile(1, irs = 0),
        )

    @Test
    fun grossMinusSsOtherAndIrs_isTheNetMonthly_forEveryProfile() {
        profiles.forEach { p ->
            val b = requireNotNull(salaryBreakdownOf(p))

            assertEquals("$p", b.netMonthlyCents, b.grossCents - b.ssCents - b.otherDeductionsCents - b.irsCents)
        }
    }

    @Test
    fun netBasePlusDuodecimos_isTheNetMonthly_forEveryProfile() {
        profiles.forEach { p ->
            val b = requireNotNull(salaryBreakdownOf(p))

            assertEquals("$p", b.netMonthlyCents, b.netBaseCents + b.netDuoCents)
        }
    }

    @Test
    fun irsIsNeverNegative_andNeverAboveTheGross() {
        profiles.forEach { p ->
            val b = requireNotNull(salaryBreakdownOf(p))

            assertTrue("$p irs=${b.irsCents}", b.irsCents >= 0)
            assertTrue("$p irs=${b.irsCents}", b.irsCents <= b.grossCents)
        }
    }
}
