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

    private fun all(p: SalaryProfile = profile): List<PlannedIncome> {
        val managed = salaryProfileLines(p, listOf(own1, own2), effectiveFrom = EARLIEST_MONTH)
        return managed + own1 + own2
    }

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
        assertEquals(SalaryComponent.HOLIDAY, managed(MANAGED_HOLIDAY).salaryComponent)
        assertEquals(SalaryComponent.CHRISTMAS, managed(MANAGED_CHRISTMAS).salaryComponent)
        assertEquals(SalaryComponent.SUBSIDIES, managed(MANAGED_DUODECIMOS).salaryComponent)
        assertEquals(SalaryComponent.EXTRA, managed(managedExtraKey("abc", 7)).salaryComponent)
        assertNull(own1.salaryComponent)
        assertNull(managed("profile_unknown").salaryComponent)
    }

    @Test
    fun everyValidManagedKey_hasAComponent() {
        val keys = lines().mapNotNull { it.managedKey }
        assertTrue(keys.all { isValidManagedKey(it) && managed(it).salaryComponent != null })
    }

    @Test
    fun extraId_mapsExtraKeys() {
        assertEquals("abc", managed(managedExtraKey("abc", 3)).salaryExtraId)
        assertNull(managed(MANAGED_MEAL).salaryExtraId)
        assertNull(own1.salaryExtraId)
    }

    private fun lines() = all()

    @Test
    fun june_withHalfSpread_keepsDuoAndHolidayLumpAsSeparateRows() {
        val rows = rowsIn(6)

        assertEquals(7_000, rows.of(SalaryComponent.SUBSIDIES).cents)
        assertEquals(39_500, rows.of(SalaryComponent.HOLIDAY).cents)
    }

    @Test
    fun nonSubsidyMonth_subsidiesRowIsTheDuoOnly() {
        assertEquals(7_000, rowsIn(8).of(SalaryComponent.SUBSIDIES).cents)
    }

    @Test
    fun noSpread_subsidiesRowIsZeroOutsideJuneAndDecember() {
        val lines = all(profile.copy(subsidySpreadPpm = SPREAD_NONE_PPM))

        assertEquals(0, rowsIn(8, lines).filter { it.component == SalaryComponent.SUBSIDIES }.sumOf { it.cents })
        assertEquals(0, rowsIn(8, lines).of(SalaryComponent.HOLIDAY).cents)
        assertEquals(79_000, rowsIn(8, lines).of(SalaryComponent.SALARY).cents)
        assertEquals(
            requireNotNull(salaryBreakdownOf(profile.copy(subsidySpreadPpm = SPREAD_NONE_PPM))).holidayLumpNetCents,
            rowsIn(6, lines).of(SalaryComponent.HOLIDAY).cents,
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
                SalaryComponent.HOLIDAY,
                SalaryComponent.CHRISTMAS,
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
    fun onlyComponentsWithLines_appear() {
        val rows = rowsIn(1, listOf(own2, own1))

        assertEquals(listOf(null, null), rows.map { it.component })
        assertEquals(listOf("own1", "own2"), rows.map { it.lines.single().id })
    }

    @Test
    fun emptyLines_noRows() {
        assertTrue(rowsIn(1, emptyList()).isEmpty())
    }
}
