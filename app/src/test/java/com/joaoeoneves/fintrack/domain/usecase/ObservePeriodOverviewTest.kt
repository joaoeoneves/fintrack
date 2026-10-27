package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.CountingBudgetRepository
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [ObservePeriodOverview]: which listeners are opened per [Period] type (via counting wrappers
 * around the fakes), which month budgets are evaluated against, and the recent-activity preview.
 */
class ObservePeriodOverviewTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    private fun noonOn(
        month: YearMonth,
        day: Int,
    ) = month.atDay(day).atTime(12, 0).toInstant(ZoneOffset.UTC)

    private val expenses = CountingExpenseRepository()
    private val income = CountingIncomeRepository()
    private val budgets = CountingBudgetRepository()
    private val categories = FakeCategoryRepository()

    private val useCase =
        ObservePeriodOverview(
            transactionRepository = CombinedTransactionRepository(expenses, income),
            budgetRepository = budgets,
            categoryRepository = categories,
            observeBudgetProgress = ObserveBudgetProgress(expenses, budgets, categories),
        )

    private suspend fun overview(
        period: Period,
        recentLimit: Int = 5,
    ) = useCase(period, now, zone, recentLimit).first()

    @Test
    fun currentMonth_opensOneExpenseOneIncomeOneBudgetListener() =
        runTest {
            val result = overview(Period.CurrentMonth)

            assertEquals(listOf(october.toInstantRange(zone)), expenses.listeners.observedRanges)
            assertEquals(listOf(october.toInstantRange(zone)), income.listeners.observedRanges)
            assertEquals(1, budgets.listeners.observedRanges.size)
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun pastMonth_opensOneExpenseListener_andBudgetsThatMonth() =
        runTest {
            val result = overview(Period.Month(september))

            assertEquals(listOf(september.toInstantRange(zone)), expenses.listeners.observedRanges)
            assertEquals(1, income.listeners.observedRanges.size)
            assertEquals(september, result.budgetMonth)
        }

    @Test
    fun rolling_opensASecondExpenseListener_forTheCurrentMonth() =
        runTest {
            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS))

            assertEquals(
                listOf(TimeRange.THREE_MONTHS.toInstantRange(now, zone), october.toInstantRange(zone)),
                expenses.listeners.observedRanges,
            )
            assertEquals(1, income.listeners.observedRanges.size)
            assertEquals(1, budgets.listeners.observedRanges.size)
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
            assertEquals(october, result.budgetMonth)
        }

    @Test
    fun sharedListener_budgetSpendOnlyCountsTheBudgetMonth() =
        runTest {
            expenses.delegate.addExpense(testExpense("sep", 7_000L, noonOn(september, 20), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("oct", 2_000L, noonOn(october, 5), BuiltInCategories.FOOD))
            budgets.delegate.setBudget(BuiltInCategories.FOOD.id, 5_000L)

            val result = overview(Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 31)))

            assertEquals(1, expenses.listeners.observedRanges.size)
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
