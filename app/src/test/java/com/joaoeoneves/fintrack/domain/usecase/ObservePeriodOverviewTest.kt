package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.recurringItem
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
    fun currentMonth_opensOneListener() =
        runTest {
            val result = overview(Period.CurrentMonth)

            val expected = listOf(october.toInstantRange(zone))
            assertEquals(expected, expenses.listeners.observedRanges)
            assertEquals(expected.map(::incomeWindow), income.listeners.observedRanges)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun pastMonth_opensOnlyThatMonth() =
        runTest {
            val result = overview(Period.Month(september))

            assertEquals(listOf(september.toInstantRange(zone)), expenses.listeners.observedRanges)
            assertEquals(listOf(incomeWindow(september.toInstantRange(zone))), income.listeners.observedRanges)
            assertEquals(september, result.budgetMonth)
        }

    @Test
    fun rolling_opensASecondExpenseAndIncomeListener_forTheCurrentMonth() =
        runTest {
            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS))

            val rolling = TimeRange.THREE_MONTHS.toInstantRange(now, zone)
            val expected = listOf(rolling, october.toInstantRange(zone))
            assertEquals(expected, expenses.listeners.observedRanges)
            assertEquals(expected.map(::incomeWindow), income.listeners.observedRanges)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun customRangeCoveringTheWholeCurrentMonth_sharesTheExpenseListener() =
        runTest {
            val result = overview(Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31)))

            assertEquals(1, expenses.listeners.observedRanges.size)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun customRangeCoveringPartOfTheCurrentMonth_opensASecondExpenseListener() =
        runTest {
            val result = overview(Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30)))

            assertEquals(2, expenses.listeners.observedRanges.size)
            assertEquals(october.toInstantRange(zone), expenses.listeners.observedRanges[1])
            assertEquals(2, income.listeners.observedRanges.size)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun noMonthBeforeListenerIsOpened() =
        runTest {
            overview(Period.CurrentMonth)

            val start = october.toInstantRange(zone).startInclusive
            assertEquals(0, expenses.listeners.observedRanges.count { it.startInclusive < start })
        }

    @Test
    fun sharedListener_budgetSpendOnlyCountsTheBudgetMonth() =
        runTest {
            expenses.delegate.addExpense(testExpense("sep", 7_000L, noonOn(september, 20), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("oct", 2_000L, noonOn(october, 5), BuiltInCategories.FOOD))
            recurring.saveRecurring(recurringItem("r1", amountCents = 5_000L, categoryId = BuiltInCategories.FOOD.id))

            val result = overview(Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31)))

            assertEquals(1, expenses.listeners.observedRanges.size)
            val food = result.budgets.single { it.category == BuiltInCategories.FOOD }
            assertEquals(2_000L, food.spentCents)
            assertEquals(5_000L, food.planned)
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
    fun budgetsListEveryCategory_withPlannedFromRecurringOnly() =
        runTest {
            recurring.saveRecurring(recurringItem("r1", amountCents = 1_000L, categoryId = BuiltInCategories.FOOD.id))

            val result = overview(Period.Month(september))

            assertEquals(BuiltInCategories.all.filterNot { it.archived }, result.budgets.map { it.category })
            assertEquals(1_000L, result.budgets.single { it.category == BuiltInCategories.FOOD }.planned)
            assertEquals(september.toInstantRange(zone), result.transactions.range)
        }
}
