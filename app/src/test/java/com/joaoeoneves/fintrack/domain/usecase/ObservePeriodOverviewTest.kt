package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * [ObservePeriodOverview]: which listeners are opened per [Period] type (via counting wrappers
 * around the fakes), which month budgets are evaluated against, and the recent-activity preview.
 */
class ObservePeriodOverviewTest : ObservePeriodOverviewTestBase() {
    @Test
    fun currentMonth_opensTheMonthAndTheMonthBeforeListeners_andOneBudgetListener() =
        runTest {
            val result = overview(Period.CurrentMonth)

            // The month and the month before it, which serves both the budget month and the range: one listener.
            val expected = listOf(october.toInstantRange(zone), monthBefore(october))
            assertEquals(expected, expenses.listeners.observedRanges)
            assertEquals(expected, income.listeners.observedRanges)
            assertEquals(1, budgets.listeners.observedRanges.size)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun pastMonth_opensTheMonthAndMonthBeforeListeners_andBudgetsThatMonth() =
        runTest {
            val result = overview(Period.Month(september))

            assertEquals(
                listOf(september.toInstantRange(zone), monthBefore(september)),
                expenses.listeners.observedRanges,
            )
            assertEquals(2, income.listeners.observedRanges.size)
            assertEquals(september, result.budgetMonth)
        }

    @Test
    fun rolling_opensASecondExpenseAndIncomeListener_forTheCurrentMonth() =
        runTest {
            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS))

            val rolling = TimeRange.THREE_MONTHS.toInstantRange(now, zone)
            val expected =
                listOf(rolling, monthBefore(october), monthBefore(rolling), october.toInstantRange(zone))
            assertEquals(expected, expenses.listeners.observedRanges)
            assertEquals(expected, income.listeners.observedRanges)
            assertEquals(1, budgets.listeners.observedRanges.size)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun customRangeCoveringTheWholeCurrentMonth_sharesTheExpenseListener() =
        runTest {
            val result = overview(Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31)))

            // The period's own listener plus the two month-before ones; no separate listener for the month.
            assertEquals(3, expenses.listeners.observedRanges.size)
            assertEquals(monthBefore(october), expenses.listeners.observedRanges[1])
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun customRangeCoveringPartOfTheCurrentMonth_opensASecondExpenseListener() =
        runTest {
            val result = overview(Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30)))

            // Both month-before ranges coincide (the range starts on the 1st), so they share one listener.
            assertEquals(3, expenses.listeners.observedRanges.size)
            assertEquals(october.toInstantRange(zone), expenses.listeners.observedRanges[2])
            assertEquals(3, income.listeners.observedRanges.size)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun monthBeforeRangesMatching_openExactlyOneMonthBeforeListener() =
        runTest {
            overview(Period.CurrentMonth)

            assertEquals(1, expenses.listeners.observedRanges.count { it == monthBefore(october) })
            assertEquals(1, income.listeners.observedRanges.count { it == monthBefore(october) })
        }

    @Test
    fun monthBeforeRangesDiffering_openTwoMonthBeforeListeners() =
        runTest {
            val range = Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31))
            overview(range)

            assertEquals(1, expenses.listeners.observedRanges.count { it == monthBefore(october) })
            assertEquals(3, expenses.listeners.observedRanges.size)
        }

    @Test
    fun sharedListener_budgetSpendOnlyCountsTheBudgetMonth() =
        runTest {
            expenses.delegate.addExpense(testExpense("sep", 7_000L, noonOn(september, 20), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("oct", 2_000L, noonOn(october, 5), BuiltInCategories.FOOD))
            budgets.delegate.setBudget(BuiltInCategories.FOOD.id, 5_000L)

            val result = overview(Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31)))

            assertEquals(3, expenses.listeners.observedRanges.size)
            val food = result.budgets.single { it.category == BuiltInCategories.FOOD }
            assertEquals(2_000L, food.spentCents)
            assertEquals(5_000L, food.limitCents)
            assertEquals(9_000L, result.summary.spendingCents)
        }

    @Test
    fun separateListener_budgetSpendMatchesTheSharedPath() =
        runTest {
            expenses.delegate.addExpense(testExpense("aug", 7_000L, noonOn(YearMonth.of(2026, 8), 20)))
            expenses.delegate.addExpense(testExpense("oct", 2_000L, noonOn(october, 5)))

            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS))

            assertEquals(2_000L, result.budgets.single { it.category == BuiltInCategories.SHOPPING }.spentCents)
            assertEquals(9_000L, result.summary.spendingCents)
        }

    @Test
    fun recentLimitRespected_newestFirst() =
        runTest {
            (1..4).forEach { day -> expenses.delegate.addExpense(testExpense("e$day", 100L, noonOn(october, day))) }
            (1..3).forEach { day -> income.delegate.addIncome(testIncome("i$day", 100L, noonOn(october, day))) }

            val result = overview(Period.CurrentMonth, recentLimit = 2)

            assertEquals(listOf("e4", "e3"), result.recent.expenses.map { it.id })
            assertEquals(listOf("i3", "i2"), result.recent.income.map { it.id })
            assertEquals(4, result.transactions.expenses.size)
        }

    @Test
    fun budgetsListBudgetRepositoryLimits() =
        runTest {
            budgets.delegate.setBudget(BuiltInCategories.RECURRING.id, 1_000L)

            val result = overview(Period.Month(september))

            assertEquals(BuiltInCategories.all.toList(), result.budgets.map { it.category })
            assertEquals(1_000L, result.budgets.single { it.category == BuiltInCategories.RECURRING }.limitCents)
            assertEquals(september.toInstantRange(zone), result.transactions.range)
        }
}
