package com.joaoeoneves.fintrack.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

/** The Home cards of [DashboardViewModel]: income (+ expected), spending, invested, leftover, and the Year scale. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelCardsTest : DashboardViewModelTestBase() {
    private val etf = userCategory("etf", kind = CategoryKind.SAVING)
    private val salary = PlannedIncome("sal", "Salary", 100_000L, 0)

    private fun build(
        expenses: List<Expense> = emptyList(),
        incomes: List<Income> = emptyList(),
        plan: List<PlannedIncome> = emptyList(),
        handle: SavedStateHandle = SavedStateHandle(),
        at: Clock = clock,
    ): DashboardViewModel {
        val planned = FakePlannedIncomeRepository()
        runBlocking { plan.forEach { planned.savePlannedIncome(it) } }
        val repos =
            DashboardRepositories(
                FakeExpenseRepository(expenses),
                FakeBudgetRepository(),
                FakeIncomeRepository(incomes),
                FakeCategoryRepository(listOf(etf)),
                planned,
            )
        return dashboardViewModel(repos, handle, at)
    }

    @Test
    fun spendingCard_countsSpendingOnly_investedCardTheSavingOutflow() =
        runTest(testDispatcher) {
            val vm =
                build(
                    listOf(
                        expense("food", 1_000L, date = noonOn(currentMonth, 3)),
                        expense("etf", 5_000L, etf, noonOn(currentMonth, 4)),
                    ),
                )
            collect(vm)

            val c = vm.content()
            assertEquals(1_000L, c.spendingCents)
            assertEquals(5_000L, c.invested?.monthCents)
        }

    @Test
    fun invested_isHiddenWhenNothingWasInvested() =
        runTest(testDispatcher) {
            val vm = build(listOf(expense("food", 1_000L, date = noonOn(currentMonth, 3))))
            collect(vm)

            assertNull(vm.content().invested)
        }

    @Test
    fun incomeCard_showsReceivedAgainstTheExpectedPlannedIncome() =
        runTest(testDispatcher) {
            val pending = build(plan = listOf(salary))
            collect(pending)
            assertEquals(0L, pending.content().incomeCents)
            assertEquals(100_000L, pending.content().expectedIncomeCents)

            val pay = income("pay_sal_202610", amountCents = 90_000L, date = noonOn(currentMonth, 2))
            val received = build(incomes = listOf(pay), plan = listOf(salary))
            collect(received)
            // Received against what the plan expected.
            assertEquals(90_000L, received.content().incomeCents)
            assertEquals(100_000L, received.content().expectedIncomeCents)
        }

    @Test
    fun leftover_isHiddenWithNoClosedMonths_andAveragesTheClosedOnesOtherwise() =
        runTest(testDispatcher) {
            val january = Clock.fixed(Instant.parse("2026-01-20T12:00:00Z"), zone)
            val jan = income("i", amountCents = 9_000L, date = noonOn(YearMonth.of(2026, 1), 2))
            val inJanuary = build(incomes = listOf(jan), at = january)
            collect(inJanuary)
            assertNull(inJanuary.content().leftover)

            val inOctober =
                build(
                    listOf(expense("e", 1_000L, date = noonOn(previousMonth, 3))),
                    listOf(income("i", amountCents = 4_000L, date = noonOn(previousMonth, 2))),
                )
            collect(inOctober)
            // January..September are closed: 3000 over 9 months.
            assertEquals(333L, inOctober.content().leftover?.averageCents)
        }

    @Test
    fun yearScale_sumsTheYear_andHasNoMonthFigures() =
        runTest(testDispatcher) {
            val vm =
                build(
                    expenses =
                        listOf(
                            expense("a", 1_000L, date = noonOn(YearMonth.of(2026, 2), 3)),
                            expense("b", 2_000L, date = noonOn(currentMonth, 3)),
                            expense("etf", 5_000L, etf, noonOn(previousMonth, 4)),
                        ),
                    incomes = listOf(income("i", amountCents = 10_000L, date = noonOn(YearMonth.of(2026, 5), 2))),
                    handle = SavedStateHandle(mapOf("period" to "year:2026")),
                )
            collect(vm)

            val c = vm.content()
            assertEquals(Period.Year(2026), c.period)
            assertEquals(3_000L, c.spendingCents)
            assertEquals(10_000L, c.incomeCents)
            assertNull(c.spendingVsAveragePercent)
            assertNull(c.invested?.monthCents)
            assertEquals(5_000L, c.invested?.yearCents)
            assertNotNull(c.invested)
        }
}
