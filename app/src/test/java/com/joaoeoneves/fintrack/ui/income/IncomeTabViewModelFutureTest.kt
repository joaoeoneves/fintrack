package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth

/** The Income tab reaches future months and years (display only) and shows the meal-card balance. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelFutureTest : IncomeTabViewModelTestBase() {
    private val oct = YearMonth.of(2026, 10)

    private fun at(period: Period) = viewModel(SavedStateHandle(mapOf("period" to period.encode())))

    private fun meal(rate: Long = 1_000L) =
        line("meal", rate).copy(
            managedKey = MANAGED_MEAL,
            basis = IncomeBasis.PER_WORKING_DAY,
        )

    @Test
    fun futureMonth_hasExpectedRows_noHistory_andCannotReceive() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 100_000L))
            val vm = at(Period.Month(YearMonth.of(2027, 3)))
            collect(vm)

            val content = vm.content()
            assertEquals(YearMonth.of(2027, 3), content.workingDays.month)
            assertNull(content.history)
            assertEquals(100_000L, content.incomeRows.single().cents)
            assertTrue(content.receipts.canReceive.isEmpty())
            assertTrue(content.receipts.receivedOn.isEmpty())
            assertNull(content.receipts.receivedPayday)
        }

    @Test
    fun futureMonth_usesTheVersionInForceThatMonth() =
        runTest(testDispatcher) {
            val raised = line("a", 120_000L).copy(versions = listOf(AmountVersion(YearMonth.of(2027, 1), 120_000L)))
            incomeRepo.savePlannedIncome(raised)

            assertEquals(
                120_000L,
                at(Period.Month(YearMonth.of(2027, 2)))
                    .also { collect(it) }
                    .content()
                    .incomeRows
                    .single()
                    .cents,
            )
        }

    @Test
    fun futureMonth_mealFollowsThatMonthsDaysOff() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(meal(1_000L))
            val target = YearMonth.of(2027, 3)
            monthSettingsRepo.setDaysOff(target, 2)
            val vm = at(Period.Month(target))
            collect(vm)

            assertEquals(
                (workingDaysIn(target) - 2) * 1_000L,
                vm
                    .content()
                    .incomeRows
                    .single()
                    .cents,
            )
        }

    private fun TestScope.canGoNext(period: Period): Boolean {
        val vm = at(period)
        collect(vm)
        return vm.content().canGoToNextMonth
    }

    @Test
    fun canGoNext_upToTenYearsAhead() =
        runTest(testDispatcher) {
            val limit = oct.plusMonths(120)

            assertTrue(canGoNext(Period.Month(limit.minusMonths(1))))
        }

    @Test
    fun canGoNext_stopsAtTenYearsAhead() =
        runTest(testDispatcher) {
            assertFalse(canGoNext(Period.Month(oct.plusMonths(120))))
        }

    @Test
    fun canGoNext_fromTheCurrentMonth() =
        runTest(testDispatcher) {
            assertTrue(canGoNext(Period.CurrentMonth))
        }

    @Test
    fun futureYear_canStillGoForward() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 100_000L))
            val vm = at(Period.Year(2027))
            collect(vm)

            assertEquals(Period.Year(2027), vm.content().period)
            assertTrue(vm.content().canGoToNextMonth)
        }

    @Test
    fun onMonthSelected_pastMonth_opensThatMonth_fromTheYearScale() =
        runTest(testDispatcher) {
            val vm = at(Period.Year(2026))
            collect(vm)

            vm.onMonthSelected(YearMonth.of(2026, 3))
            advanceUntilIdle()

            assertEquals(Period.Month(YearMonth.of(2026, 3)), vm.content().period)
        }

    @Test
    fun onMonthSelected_currentMonth_resolvesToCurrentMonth() =
        runTest(testDispatcher) {
            val vm = at(Period.Year(2026))
            collect(vm)

            vm.onMonthSelected(oct)
            advanceUntilIdle()

            assertEquals(Period.CurrentMonth, vm.content().period)
        }

    @Test
    fun mealCard_month_receivedSpentAndNegativeBalance() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(meal())
            actualIncomeRepo.addIncome(testIncome("pay_meal_202609", 20_000L, Instant.parse("2026-09-30T10:00:00Z")))
            val card = testExpense("e1", 25_000L, Instant.parse("2026-10-06T10:00:00Z"))
            val bank = testExpense("e2", 9_000L, Instant.parse("2026-10-07T10:00:00Z"))
            expenseRepo.addExpense(card.copy(paidWithMealCard = true))
            expenseRepo.addExpense(bank)
            val vm = viewModel(mealCardAvailable = flowOf(true))
            collect(vm)

            val balance = vm.content().mealCard!!
            assertEquals(20_000L, balance.plafondCents)
            assertEquals(25_000L, balance.spentCents)
            assertEquals(-5_000L, balance.balanceCents)
        }

    private fun TestScope.yearWithMeal(available: Boolean): IncomeTabViewModel {
        val handle = SavedStateHandle(mapOf("period" to Period.Year(2026).encode()))
        val vm = viewModel(handle, mealCardAvailable = flowOf(available))
        collect(vm)
        return vm
    }

    private suspend fun seedMealYear() {
        incomeRepo.savePlannedIncome(meal())
        actualIncomeRepo.addIncome(testIncome("pay_meal_202601", 10_000L, Instant.parse("2026-01-31T10:00:00Z")))
        actualIncomeRepo.addIncome(testIncome("pay_meal_202610", 20_000L, Instant.parse("2026-10-05T10:00:00Z")))
        expenseRepo.addExpense(
            testExpense("e1", 7_000L, Instant.parse("2026-03-06T10:00:00Z")).copy(paidWithMealCard = true),
        )
    }

    @Test
    fun mealCard_year_sumsTheYear() =
        runTest(testDispatcher) {
            seedMealYear()
            val balance = yearWithMeal(true).content().mealCard!!

            assertEquals(30_000L, balance.plafondCents)
            assertEquals(7_000L, balance.spentCents)
        }

    @Test
    fun mealCard_year_isNullWhenUnavailable() =
        runTest(testDispatcher) {
            seedMealYear()

            assertNull(yearWithMeal(false).content().mealCard)
        }
}
