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
            extras = listOf(testExtra(), multipleExtra(months = setOf(4, 10)), fixedExtra()),
        )
    private val own1 = PlannedIncome("own1", "Rent", 10_000, order = 100)
    private val own2 = PlannedIncome("own2", "Gig", 5_000, order = 101)

    private fun all(p: SalaryProfile = profile) = salaryProfileLines(p, listOf(own1, own2)) + own1 + own2

    private fun rowsIn(
        month: Int,
        lines: List<PlannedIncome> = all(),
    ) = incomeRowsIn(lines, YearMonth.of(2026, month), days)

    private fun List<IncomeRow>.of(component: SalaryComponent) = single { it.component == component }

    private fun List<IncomeRow>.extra(id: String) = single { it.extraId == id }

    @Test
    fun component_mapsEveryKey() {
        assertEquals(SalaryComponent.SALARY, managed(MANAGED_NET_SALARY).salaryComponent)
        assertEquals(SalaryComponent.MEAL, managed(MANAGED_MEAL).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_HOLIDAY).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_CHRISTMAS).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_DUODECIMOS).salaryComponent)
        assertEquals(SalaryComponent.EXTRA, managed(MANAGED_BONUS).salaryComponent)
        assertEquals(SalaryComponent.EXTRA, managed(managedProfitShareKey(7)).salaryComponent)
        assertEquals(SalaryComponent.EXTRA, managed(managedExtraKey("abc", 7)).salaryComponent)
        for (i in 0 until 4) {
            assertEquals(SalaryComponent.EXTRA, managed(managedFlexKey(i)).salaryComponent)
        }
        assertNull(own1.salaryComponent)
        assertNull(managed("profile_unknown").salaryComponent)
    }

    @Test
    fun everyValidManagedKey_hasAComponent() {
        val keys = lines().mapNotNull { it.managedKey } + managedFlexKey(0) + MANAGED_BONUS
        assertTrue(keys.all { isValidManagedKey(it) && managed(it).salaryComponent != null })
    }

    @Test
    fun extraId_mapsExtraAndLegacyKeys() {
        assertEquals("abc", managed(managedExtraKey("abc", 3)).salaryExtraId)
        assertEquals(LEGACY_FLEX_ID, managed(managedFlexKey(2)).salaryExtraId)
        assertEquals(LEGACY_PROFIT_SHARE_ID, managed(managedProfitShareKey(5)).salaryExtraId)
        assertEquals(LEGACY_PROFIT_SHARE_ID, managed(MANAGED_PROFIT_SHARE).salaryExtraId)
        assertEquals(LEGACY_BONUS_ID, managed(MANAGED_BONUS).salaryExtraId)
        assertNull(managed(MANAGED_MEAL).salaryExtraId)
        assertNull(own1.salaryExtraId)
    }

    private fun lines() = all()

    @Test
    fun june_withHalfSpread_mergesDuoAndLumpIntoOneSubsidiesRow() {
        val rows = rowsIn(6)

        // duo 7000 + lump 39500
        assertEquals(46_500, rows.of(SalaryComponent.SUBSIDIES).cents)
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
        // Paid in 1, 4, 7, 10.
        assertEquals(0, rowsIn(2).extra("flex").cents)
        assertEquals(24_500, rowsIn(4).extra("flex").cents)
    }

    @Test
    fun oneRowPerExtra_managedFirstInOrder_thenManualLines() {
        val rows = rowsIn(6)

        assertEquals(
            listOf(
                SalaryComponent.SALARY,
                SalaryComponent.MEAL,
                SalaryComponent.SUBSIDIES,
                SalaryComponent.EXTRA,
                SalaryComponent.EXTRA,
                SalaryComponent.EXTRA,
                null,
                null,
            ),
            rows.map { it.component },
        )
        assertEquals(listOf("flex", "profit", "bonus"), rows.mapNotNull { it.extraId })
        assertEquals(listOf("own1", "own2"), rows.takeLast(2).map { it.lines.single().id })
        assertEquals(listOf(10_000L, 5_000L), rows.takeLast(2).map { it.cents })
    }

    @Test
    fun extraRows_followTheProfileOrderOfTheExtras() {
        val reordered = profile.copy(extras = listOf(fixedExtra(), testExtra()))

        val rows = rowsIn(6, all(reordered))

        assertEquals(listOf("bonus", "flex"), rows.mapNotNull { it.extraId })
    }

    @Test
    fun extraMonths_areOneRow() {
        val rows = rowsIn(4)

        assertEquals(2, rows.extra("profit").lines.size)
        assertEquals(25_000, rows.extra("profit").cents)
    }

    @Test
    fun legacyLines_groupWithTheirMigratedExtraId() {
        val legacy =
            listOf(managedFlexKey(0), managedFlexKey(1), MANAGED_BONUS, managedProfitShareKey(3))
                .mapIndexed { i, k ->
                    PlannedIncome(k, "old", 100, i, frequency = IncomeFrequency.MONTHLY, managedKey = k)
                }

        val rows = rowsIn(1, legacy)

        assertEquals(listOf("flex", "profitshare", "bonus").toSet(), rows.mapNotNull { it.extraId }.toSet())
        assertEquals(200, rows.extra("flex").cents)
        assertEquals(2, rows.extra("flex").lines.size)
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
        assertEquals(12 * 7_000L + 79_000, rows.of(SalaryComponent.SUBSIDIES).cents)
        assertEquals(12 * 900L * days, rows.of(SalaryComponent.MEAL).cents)
        assertEquals(listOf(1, 4, 7, 10), rows.extra("flex").months)
        assertEquals(listOf(4, 10), rows.extra("profit").months)
        assertEquals(listOf(5), rows.extra("bonus").months)
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
