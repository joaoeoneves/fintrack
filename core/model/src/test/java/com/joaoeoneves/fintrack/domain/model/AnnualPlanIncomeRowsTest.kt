package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.bonusExtra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualPlanIncomeRowsTest {
    private val profile =
        SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, extras = listOf(bonusExtra()))
    private val own = PlannedIncome("own", "Gig", 5_000, order = 50)

    private fun plan(lines: List<PlannedIncome>) = annualPlanOf(PLAN_YEAR, planInputs(lines = lines))

    @Test
    fun rows_areFilled_andTotalsMatchTheLines() {
        val lines = salaryProfileLines(profile, listOf(own)) + own

        val income = plan(lines).income

        assertEquals(income.totalCents, income.rows.sumOf { it.cents })
        assertEquals(
            listOf(SalaryComponent.SALARY, SalaryComponent.SUBSIDIES, SalaryComponent.EXTRA, null),
            income.rows.map { it.component },
        )
        assertEquals(listOf(5), income.rows.single { it.extraId == "bonus" }.months)
        assertEquals((1..12).toList(), income.rows.first().months)
    }

    @Test
    fun totalCents_isUnchangedByTheSplit() {
        val split = salaryProfileLines(profile, emptyList())
        val b = requireNotNull(salaryBreakdownOf(profile))

        val total = plan(split).income.totalCents

        assertEquals(12 * b.netMonthlyCents + 2 * b.holidayLumpNetCents + 10_000, total)
    }

    @Test
    fun noLines_noRows() {
        assertTrue(plan(emptyList()).income.rows.isEmpty())
    }
}
