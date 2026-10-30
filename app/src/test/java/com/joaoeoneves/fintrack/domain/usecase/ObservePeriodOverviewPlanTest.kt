package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.assertFailsWithError
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [ObservePeriodOverview] budget re-emission and error paths, and the monthly plan it assembles. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObservePeriodOverviewPlanTest : ObservePeriodOverviewTestBase() {
    private fun List<CategoryBudget>.food() = single { it.category == BuiltInCategories.FOOD }

    private val salary = PlannedIncome(id = "salary", label = "Salary", amountCents = 200_000L, order = 0)

    @Test
    fun recurringChange_reEmits_withMonthSpendOnly() =
        runTest {
            expenses.delegate.addExpense(testExpense("oct", 4_000L, noonOn(october, 31), BuiltInCategories.FOOD))
            val november = noonOn(october.plusMonths(1), 1)
            expenses.delegate.addExpense(testExpense("nov", 9_000L, november, BuiltInCategories.FOOD))
            val emissions = mutableListOf<PeriodOverview>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                useCase(Period.CurrentMonth, now, zone, 5).toList(emissions)
            }

            recurring.saveRecurring(recurringItem("f1", amountCents = 3_000L, categoryId = BuiltInCategories.FOOD.id))

            val before = emissions.first().budgets.food()
            val after = emissions.last().budgets.food()
            assertEquals(4_000L, before.spentCents)
            assertNull(before.planned)
            assertEquals(3_000L, after.planned)
            assertTrue(after.isOverBudget)
        }

    @Test
    fun plannedIncomeFlowError_propagates() =
        runTest {
            val error = IllegalStateException("plan down")
            plannedIncome.nextObserveError = error

            assertFailsWithError(error) { overview(Period.CurrentMonth) }
        }

    @Test
    fun noPlannedIncome_planIsNotConfigured_withActualIncomeOfTheMonth() =
        runTest {
            income.delegate.addIncome(testIncome("pay", 150_000L, noonOn(october, 2)))

            val plan = overview(Period.CurrentMonth).plan

            assertFalse(plan.isConfigured)
            assertEquals(0L, plan.expectedIncomeCents)
            assertEquals(150_000L, plan.actualIncomeCents)
        }

    @Test
    fun plan_combinesPlannedIncomeRecurringAndSpend() =
        runTest {
            plannedIncome.savePlannedIncome(salary)
            recurring.saveRecurring(recurringItem("f1", amountCents = 30_000L, categoryId = BuiltInCategories.FOOD.id))
            expenses.delegate.addExpense(testExpense("f", 5_000L, noonOn(october, 3), BuiltInCategories.FOOD))

            val plan = overview(Period.CurrentMonth).plan

            assertTrue(plan.isConfigured)
            assertEquals(200_000L, plan.expectedIncomeCents)
            assertEquals(30_000L, plan.plannedSpendingCents)
            assertEquals(25_000L, plan.remainingInBucketsCents)
            assertEquals(170_000L, plan.safeToSpendCents)
        }

    @Test
    fun rollingPeriod_planUsesOnlyTheBudgetMonthsIncome() =
        runTest {
            income.delegate.addIncome(testIncome("aug", 90_000L, noonOn(october.minusMonths(2), 5)))
            income.delegate.addIncome(testIncome("oct", 10_000L, noonOn(october, 5)))

            val result = overview(Period.Rolling(TimeRange.ONE_YEAR))

            assertEquals(10_000L, result.plan.actualIncomeCents)
            assertEquals(2, income.listeners.observedRanges.size)
            assertEquals(100_000L, result.summary.incomeCents)
        }

    @Test
    fun enclosingCustomRange_planIgnoresIncomeOutsideTheBudgetMonth() =
        runTest {
            income.delegate.addIncome(testIncome("sep", 70_000L, noonOn(september, 20)))
            income.delegate.addIncome(testIncome("oct", 5_000L, noonOn(october, 5)))

            val result = overview(Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31)))

            assertEquals(5_000L, result.plan.actualIncomeCents)
        }

    @Test
    fun partialMonthPeriod_usesSecondListenerForTheMonthsIncome() =
        runTest {
            income.delegate.addIncome(testIncome("early", 40_000L, noonOn(october, 2)))
            income.delegate.addIncome(testIncome("recent", 1_000L, noonOn(october, 19)))

            val result = overview(Period.Custom(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 20)))

            assertEquals(41_000L, result.plan.actualIncomeCents)
            assertEquals(2, income.listeners.observedRanges.size)
            assertEquals(incomeWindow(october.toInstantRange(zone)), income.listeners.observedRanges[1])
            assertEquals(1_000L, result.summary.incomeCents)
        }

    @Test
    fun pastMonth_planEvaluatedForThatMonth() =
        runTest {
            income.delegate.addIncome(testIncome("sep", 33_000L, noonOn(september, 3)))
            income.delegate.addIncome(testIncome("oct", 99_000L, noonOn(october, 3)))

            val result = overview(Period.Month(september))

            assertEquals(september, result.budgetMonth)
            assertEquals(33_000L, result.plan.actualIncomeCents)
        }

    @Test
    fun plannedIncomeChange_reEmitsThePlan() =
        runTest {
            val emissions = mutableListOf<PeriodOverview>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                useCase(Period.CurrentMonth, now, zone, 5).toList(emissions)
            }

            plannedIncome.savePlannedIncome(salary)
            plannedIncome.deletePlannedIncome("salary")

            assertEquals(listOf(false, true, false), emissions.map { it.plan.isConfigured })
            assertEquals(200_000L, emissions[1].plan.expectedIncomeCents)
        }

    private val perDay =
        PlannedIncome("meal", "Meal", 1046L, 0, basis = IncomeBasis.PER_WORKING_DAY)

    @Test
    fun perWorkingDayIncome_usesCalendarDays_withoutSettings() =
        runTest {
            plannedIncome.savePlannedIncome(perDay)

            assertEquals(1046L * 21, overview(Period.CurrentMonth).plan.expectedIncomeCents)
        }

    @Test
    fun daysOff_reduceThePerWorkingDayIncome() =
        runTest {
            plannedIncome.savePlannedIncome(perDay)
            monthSettings.setDaysOff(october, 3)

            assertEquals(1046L * (21 - 3), overview(Period.CurrentMonth).plan.expectedIncomeCents)
        }

    @Test
    fun daysOff_beyondTheCalendarCount_clampIncomeAtZero() =
        runTest {
            plannedIncome.savePlannedIncome(perDay)
            monthSettings.setDaysOff(october, MAX_DAYS_OFF)

            assertEquals(0L, overview(Period.CurrentMonth).plan.expectedIncomeCents)
        }

    @Test
    fun daysOffInAnotherMonth_doNotAffectTheBudgetMonth() =
        runTest {
            plannedIncome.savePlannedIncome(perDay)
            monthSettings.setDaysOff(september, 5)

            assertEquals(1046L * 21, overview(Period.CurrentMonth).plan.expectedIncomeCents)
        }

    @Test
    fun daysOff_doNotChangeFixedIncome() =
        runTest {
            plannedIncome.savePlannedIncome(salary)
            monthSettings.setDaysOff(october, 3)

            assertEquals(200_000L, overview(Period.CurrentMonth).plan.expectedIncomeCents)
        }
}
