package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** [annualPlanOf] with the salary paying for the next month, and the separate savings line. */
class AnnualPlanShiftTest {
    private val salary = PlannedIncome("sal", "Salary", 100_000L, 0, managedKey = "salary")

    @Test
    fun shiftedYear_stillHasTwelveSalaries() {
        val inputs = planInputs(lines = listOf(salary))
        val plain = annualPlanOf(PLAN_YEAR, inputs)
        val shifted = annualPlanOf(PLAN_YEAR, inputs.copy(shiftSalary = true))

        assertEquals(12 * 100_000L, plain.income.totalCents)
        assertEquals(12 * 100_000L, shifted.income.totalCents)
    }

    @Test
    fun shiftedYear_perWorkingDayLine_usesThePreviousMonthsWorkingDays() {
        val perDay = salary.copy(basis = IncomeBasis.PER_WORKING_DAY, amountCents = 1_000L)
        val inputs = planInputs(lines = listOf(perDay))
        val plain = annualPlanOf(PLAN_YEAR, inputs).income.totalCents
        val shifted = annualPlanOf(PLAN_YEAR, inputs.copy(shiftSalary = true)).income.totalCents

        assertEquals((1..12).sumOf { 1_000L * effectiveWorkingDays(planMonth(it).minusMonths(1), null, 0) }, shifted)
        assertEquals((1..12).sumOf { 1_000L * effectiveWorkingDays(planMonth(it), null, 0) }, plain)
    }

    @Test
    fun manualLinesAreNotShifted() {
        val inputs = planInputs(lines = listOf(planPerDay("man", 1_000L)))

        assertEquals(
            annualPlanOf(PLAN_YEAR, inputs).income.totalCents,
            annualPlanOf(PLAN_YEAR, inputs.copy(shiftSalary = true)).income.totalCents,
        )
    }

    @Test
    fun savingsAreOneSeparateLine_notAnEnvelope() {
        val saving = BuiltInCategories.INVESTMENTS
        val inputs =
            planInputs(
                lines = listOf(planFixed("inc", 100_000L)),
                budgets = listOf(Budget(saving.id, 10_000L)),
                expenses = listOf(planExpense("s", saving, 4_000L), planExpense("r", saving, 1_000L, refund = true)),
            )

        val plan = annualPlanOf(PLAN_YEAR, inputs)

        assertEquals(AnnualSavings(12 * 10_000L, 3_000L), plan.savings)
        assertEquals(emptyList<AnnualEnvelope>(), plan.envelopes)
        assertEquals(12 * 10_000L, plan.plannedCents)
        assertEquals(12 * 100_000L - 12 * 10_000L, plan.leftoverCents)
    }

    @Test
    fun noSavings_isZero() {
        assertEquals(AnnualSavings(0L, 0L), annualPlanOf(PLAN_YEAR, planInputs()).savings)
    }
}
