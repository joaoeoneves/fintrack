package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class IncomeComponentsTest {
    private val days = 20

    private fun managed(
        key: String,
        order: Int = 0,
    ) = PlannedIncome(key, key, 1, order, managedKey = key)

    private val profile =
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            irsSubsidiesPpm = 50_000,
            mealRatePerDayCents = 900,
            flexPpm = 70_000,
            profitShareMultiplierPpm = 500_000,
            profitShareMonths = setOf(4, 10),
            bonusCents = 10_000,
            bonusMonth = 5,
        )
    private val own1 = PlannedIncome("own1", "Rent", 10_000, order = 100)
    private val own2 = PlannedIncome("own2", "Gig", 5_000, order = 101)

    private fun all(p: SalaryProfile = profile) = salaryProfileLines(p, listOf(own1, own2)) + own1 + own2

    private fun rowsIn(
        month: Int,
        lines: List<PlannedIncome> = all(),
    ) = incomeRowsIn(lines, YearMonth.of(2026, month), days)

    private fun List<IncomeRow>.of(component: SalaryComponent) = single { it.component == component }

    @Test
    fun component_mapsEveryKey() {
        assertEquals(SalaryComponent.SALARY, managed(MANAGED_NET_SALARY).salaryComponent)
        assertEquals(SalaryComponent.MEAL, managed(MANAGED_MEAL).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_HOLIDAY).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_CHRISTMAS).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_DUODECIMOS).salaryComponent)
        assertEquals(SalaryComponent.BONUS, managed(MANAGED_BONUS).salaryComponent)
        assertEquals(SalaryComponent.PROFIT_SHARE, managed(MANAGED_PROFIT_SHARE).salaryComponent)
        assertEquals(SalaryComponent.PROFIT_SHARE, managed(managedProfitShareKey(7)).salaryComponent)
        for (i in 0 until FLEX_PAYMENTS) {
            assertEquals(SalaryComponent.FLEX, managed(managedFlexKey(i)).salaryComponent)
        }
        assertNull(own1.salaryComponent)
        assertNull(managed("profile_unknown").salaryComponent)
    }

    @Test
    fun everyManagedKey_hasAComponent() {
        assertTrue(MANAGED_KEYS.all { managed(it).salaryComponent != null })
    }

    @Test
    fun june_withHalfSpread_mergesDuoAndLumpIntoOneSubsidiesRow() {
        val rows = rowsIn(6)

        // duo 7000 + lump 42000
        assertEquals(49_000, rows.of(SalaryComponent.SUBSIDIES).cents)
        assertEquals(3, rows.of(SalaryComponent.SUBSIDIES).lines.size)
    }

    @Test
    fun nonSubsidyMonth_subsidiesRowIsTheDuoOnly() {
        assertEquals(7_000, rowsIn(8).of(SalaryComponent.SUBSIDIES).cents)
    }

    @Test
    fun noSpread_subsidiesRowIsZeroOutsideJuneAndDecember() {
        val lines = all(profile.copy(subsidySpreadPpm = SPREAD_NONE_PPM))

        assertEquals(0, rowsIn(8, lines).of(SalaryComponent.SUBSIDIES).cents)
        assertEquals(79_000, rowsIn(8, lines).of(SalaryComponent.SALARY).cents)
        assertEquals(
            requireNotNull(salaryBreakdownOf(profile.copy(subsidySpreadPpm = SPREAD_NONE_PPM))).holidayLumpNetCents,
            rowsIn(6, lines).of(SalaryComponent.SUBSIDIES).cents,
        )
    }

    @Test
    fun flexIsZeroOutsideItsQuarters() {
        // First month 1 -> paid in 1, 4, 7, 10.
        assertEquals(0, rowsIn(2).of(SalaryComponent.FLEX).cents)
        assertEquals(24_500, rowsIn(4).of(SalaryComponent.FLEX).cents)
    }

    @Test
    fun exactlyOneRowPerComponent_managedFirstInEnumOrder_thenManualLines() {
        val rows = rowsIn(6)

        assertEquals(
            listOf(
                SalaryComponent.SALARY,
                SalaryComponent.MEAL,
                SalaryComponent.SUBSIDIES,
                SalaryComponent.FLEX,
                SalaryComponent.PROFIT_SHARE,
                SalaryComponent.BONUS,
                null,
                null,
            ),
            rows.map { it.component },
        )
        assertEquals(listOf("own1", "own2"), rows.takeLast(2).map { it.lines.single().id })
        assertEquals(listOf(10_000L, 5_000L), rows.takeLast(2).map { it.cents })
    }

    @Test
    fun profitShareMonths_areOneRow() {
        val rows = rowsIn(4)

        assertEquals(2, rows.of(SalaryComponent.PROFIT_SHARE).lines.size)
        assertEquals(25_000, rows.of(SalaryComponent.PROFIT_SHARE).cents)
    }

    @Test
    fun onlyComponentsWithLines_appear() {
        val rows = rowsIn(1, listOf(own2, own1))

        assertEquals(listOf(null, null), rows.map { it.component })
        assertEquals(listOf("own1", "own2"), rows.map { it.lines.single().id })
    }

    @Test
    fun emptyLines_noRows() {
        assertTrue(rowsIn(1, emptyList()).isEmpty())
    }

    @Test
    fun annualRows_sumTheYear_andListPayingMonths() {
        val year = (1..12).associate { YearMonth.of(2026, it) to days }

        val rows = annualIncomeRowsOf(all(), year)

        assertEquals(12 * 79_000L, rows.of(SalaryComponent.SALARY).cents)
        assertEquals((1..12).toList(), rows.of(SalaryComponent.SALARY).months)
        assertEquals(12 * 7_000L + 84_000, rows.of(SalaryComponent.SUBSIDIES).cents)
        assertEquals(12 * 900L * days, rows.of(SalaryComponent.MEAL).cents)
        assertEquals(listOf(1, 4, 7, 10), rows.of(SalaryComponent.FLEX).months)
        assertEquals(listOf(4, 10), rows.of(SalaryComponent.PROFIT_SHARE).months)
        assertEquals(listOf(5), rows.of(SalaryComponent.BONUS).months)
        assertEquals(listOf(null, null), rows.takeLast(2).map { it.component })
        assertEquals(12 * 10_000L, rows[rows.size - 2].cents)
    }

    @Test
    fun annualRows_dropRowsWithNothingInTheYear() {
        val once =
            PlannedIncome("once", "Once", 100, 0, frequency = IncomeFrequency.ONCE, yearMonth = YearMonth.of(2027, 3))
        val lines = listOf(once)
        val year = (1..12).associate { YearMonth.of(2026, it) to days }

        assertTrue(annualIncomeRowsOf(lines, year).isEmpty())
    }
}
