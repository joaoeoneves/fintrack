package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LegacySalaryExtras.toExtras]: the migration of the old fixed flex / profit-share / bonus fields. */
class LegacySalaryExtrasTest {
    private val gross = SalaryInputMode.GROSS
    private val net = SalaryInputMode.NET

    private fun List<SalaryExtra>.byId(id: String) = single { it.id == id }

    @Test
    fun nothingLegacy_isEmpty() {
        assertFalse(LegacySalaryExtras().hasAny)
        assertTrue(LegacySalaryExtras().toExtras(gross).isEmpty())
        assertTrue(LegacySalaryExtras().toExtras(net).isEmpty())
        val zeros = LegacySalaryExtras(flexPpm = 0, profitShareMultiplierPpm = 0, bonusCents = 0)
        assertTrue(zeros.toExtras(gross).isEmpty())
    }

    @Test
    fun hasAny_isTrueForEachField() {
        assertTrue(LegacySalaryExtras(flexPpm = 1).hasAny)
        assertTrue(LegacySalaryExtras(flexAnnualCents = 1).hasAny)
        assertTrue(LegacySalaryExtras(profitShareMultiplierPpm = 1).hasAny)
        assertTrue(LegacySalaryExtras(bonusCents = 1).hasAny)
    }

    @Test
    fun gross_everythingMigrates_withFixedIdsAndNames() {
        val legacy =
            LegacySalaryExtras(
                flexPpm = 70_000,
                flexFirstMonth = 2,
                flexWithdrawalFeePpm = 5_000,
                profitShareMultiplierPpm = 500_000,
                profitShareMonths = setOf(4, 10),
                bonusCents = 10_000,
                bonusMonth = 5,
            )

        val result = legacy.toExtras(gross)

        assertEquals(listOf("flex", "profitshare", "bonus"), result.map { it.id })
        assertEquals(listOf("Flexible benefits", "Profit share", "Bonus"), result.map { it.name })
        assertEquals(
            testExtra(id = "flex", name = "Flexible benefits", months = setOf(2, 5, 8, 11), feePpm = 5_000),
            result.byId("flex"),
        )
        assertEquals(
            multipleExtra(id = "profitshare", name = "Profit share", months = setOf(4, 10)),
            result.byId("profitshare"),
        )
        assertEquals(
            fixedExtra(id = "bonus", name = "Bonus", deductions = SalaryExtraDeductions.IRS_SS),
            result.byId("bonus"),
        )
    }

    @Test
    fun net_flexIsFixed_noFee_profitShareDropped_bonusHasNoDeductions() {
        val legacy =
            LegacySalaryExtras(
                flexPpm = 70_000,
                flexAnnualCents = 100_000,
                flexWithdrawalFeePpm = 5_000,
                profitShareMultiplierPpm = 500_000,
                bonusCents = 10_000,
                bonusMonth = 5,
            )

        val result = legacy.toExtras(net)

        assertEquals(listOf("flex", "bonus"), result.map { it.id })
        assertEquals(SalaryExtraAmountType.FIXED_CENTS, result.byId("flex").amountType)
        assertEquals(100_000L, result.byId("flex").amount)
        assertEquals(0, result.byId("flex").feePpm)
        assertEquals(SalaryExtraDeductions.NONE, result.byId("bonus").deductions)
    }

    @Test
    fun net_withOnlyPercentFlex_migratesNothing_andGrossIgnoresAnnualCents() {
        assertTrue(LegacySalaryExtras(flexPpm = 70_000).toExtras(net).isEmpty())
        assertTrue(LegacySalaryExtras(flexAnnualCents = 5_000).toExtras(gross).isEmpty())
    }

    @Test
    fun flexMonths_fromJanuary_march_andWrapping() {
        fun months(first: Int) = LegacySalaryExtras(flexPpm = 1, flexFirstMonth = first).toExtras(gross).single().months

        assertEquals(setOf(1, 4, 7, 10), months(1))
        assertEquals(setOf(3, 6, 9, 12), months(3))
        assertEquals(setOf(11, 2, 5, 8), months(11))
        assertEquals(setOf(12, 3, 6, 9), months(12))
    }

    @Test
    fun profitShareMonths_fallbackToTheLegacySingleMonth_thenMarch() {
        fun months(legacy: LegacySalaryExtras) = legacy.toExtras(gross).single().months

        val base = LegacySalaryExtras(profitShareMultiplierPpm = 1_000_000)
        assertEquals(setOf(2, 5), months(base.copy(profitShareMonths = setOf(5, 2), profitShareMonth = 8)))
        assertEquals(setOf(8), months(base.copy(profitShareMonth = 8)))
        assertEquals(setOf(DEFAULT_EXTRA_PAY_MONTH), months(base))
    }

    @Test
    fun bonusMonth_defaultsToMarch() {
        assertEquals(setOf(3), LegacySalaryExtras(bonusCents = 1).toExtras(gross).single().months)
    }

    @Test
    fun migratedGrossExtras_produceTheLegacyAmounts() {
        val legacy =
            LegacySalaryExtras(flexPpm = 70_000, profitShareMultiplierPpm = 500_000, profitShareMonths = setOf(4))
        val profile = SalaryProfile(baseGrossCents = 100_000, extras = legacy.toExtras(gross))

        val b = requireNotNull(salaryBreakdownOf(profile))

        assertEquals(listOf(98_000L, 50_000L), b.extras.map { it.totalCents })
    }
}
