package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.testutil.genericErrorText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Income, net balance and savings-rate tests for [DashboardViewModel], plus observeIncome's
 * error-state handling. See [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelIncomeTest : DashboardViewModelTestBase() {
    private val delta = 1e-9

    @Test
    fun incomeCents_sumsIncomeWithinCurrentMonth_excludesOtherMonths() =
        runTest(testDispatcher) {
            val inside = income(id = "inside", amountCents = 250_000L, date = daysAgo(3))
            val lastMonth = income(id = "sep", amountCents = 999_999L, date = noonOn(previousMonth, 30))
            val viewModel = viewModel(incomeRepository = FakeIncomeRepository(listOf(inside, lastMonth)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(250_000L, content.incomeCents)
            assertEquals(listOf(inside), content.recentIncome)
        }

    @Test
    fun emptyIncome_isZero_andSavingsRateIsNull() =
        runTest(testDispatcher) {
            val investment =
                expense(id = "i", amountCents = 5_000L, category = BuiltInCategories.INVESTMENTS, date = daysAgo(1))
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(investment)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(0L, content.incomeCents)
            assertTrue(content.recentIncome.isEmpty())
            assertNull(content.savingsRate)
            assertEquals(-5_000L, content.netCents)
        }

    @Test
    fun netCents_isIncomeMinusSpendingMinusSaved_andSavingsRateIsSavedOverIncome() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense("shop", 12_000L, BuiltInCategories.SHOPPING, daysAgo(2)),
                    expense("rent", 80_000L, BuiltInCategories.RECURRING, daysAgo(3)),
                    expense("etf", 75_000L, BuiltInCategories.INVESTMENTS, daysAgo(4)),
                )
            val salary = income(id = "salary", amountCents = 300_000L, date = daysAgo(1))
            val viewModel =
                viewModel(
                    expenseRepository = FakeExpenseRepository(expenses),
                    incomeRepository = FakeIncomeRepository(listOf(salary)),
                )
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(300_000L, content.incomeCents)
            assertEquals(92_000L, content.spendingCents)
            assertEquals(75_000L, content.savedCents)
            assertEquals(167_000L, content.outflowCents)
            assertEquals(133_000L, content.netCents)
            assertEquals(0.25, content.savingsRate!!, delta)
        }

    @Test
    fun netCents_negativeWhenOutflowExceedsIncome_savingsRateAboveOneWhenSavedExceedsIncome() =
        runTest(testDispatcher) {
            val etf = expense(id = "etf", amountCents = 50_000L, category = BuiltInCategories.INVESTMENTS, date = now)
            val small = income(id = "i1", amountCents = 10_000L, date = now)
            val viewModel =
                viewModel(
                    expenseRepository = FakeExpenseRepository(listOf(etf)),
                    incomeRepository = FakeIncomeRepository(listOf(small)),
                )
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(-40_000L, content.netCents)
            assertEquals(5.0, content.savingsRate!!, delta)
        }

    @Test
    fun incomeWithoutSavings_hasZeroSavingsRate_notNull() =
        runTest(testDispatcher) {
            val salary = income(id = "i", amountCents = 1_000L, date = now)
            val viewModel = viewModel(incomeRepository = FakeIncomeRepository(listOf(salary)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(0.0, content.savingsRate!!, delta)
            assertEquals(1_000L, content.netCents)
        }

    @Test
    fun netCents_zeroWhenNoIncomeAndNoExpenses() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(0L, content.incomeCents)
            assertEquals(0L, content.outflowCents)
            assertEquals(0L, content.netCents)
            assertNull(content.savingsRate)
        }

    @Test
    fun recentIncome_returnsTop5ByDateDescending_whenMoreThan5InRange() =
        runTest(testDispatcher) {
            val all = (1..7).map { income(id = "i$it", amountCents = 100L, date = daysAgo(it.toLong())) }
            val scrambled = listOf(all[3], all[0], all[6], all[1], all[5], all[2], all[4])
            val viewModel = viewModel(incomeRepository = FakeIncomeRepository(scrambled))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(all.take(5), content.recentIncome)
            assertEquals(700L, content.incomeCents)
        }

    @Test
    fun selectingPreviousMonth_switchesIncomeAndExpensesTogether() =
        runTest(testDispatcher) {
            val sepIncome = income(id = "sep-in", amountCents = 400_000L, date = noonOn(previousMonth, 25))
            val octIncome = income(id = "oct-in", amountCents = 100_000L, date = daysAgo(1))
            val sepExpense = expense(id = "sep-ex", amountCents = 5_000L, date = noonOn(previousMonth, 2))
            val viewModel =
                viewModel(
                    expenseRepository = FakeExpenseRepository(listOf(sepExpense)),
                    incomeRepository = FakeIncomeRepository(listOf(sepIncome, octIncome)),
                )
            collect(viewModel)
            assertEquals(100_000L, viewModel.content().netCents)

            viewModel.onPeriodSelected(Period.Month(previousMonth))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(400_000L, content.incomeCents)
            assertEquals(5_000L, content.outflowCents)
            assertEquals(395_000L, content.netCents)
            assertEquals(listOf(sepIncome), content.recentIncome)
        }

    // ---- observeIncome failure -> Error state ----

    @Test
    fun observeIncomeFailure_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository()
            incomeRepo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val viewModel = viewModel(incomeRepository = incomeRepo)
            collect(viewModel)

            assertEquals(DashboardUiState.Error(genericErrorText), viewModel.uiState.value)
        }

    @Test
    fun observeIncomeFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository()
            incomeRepo.nextObserveIncomeError = RuntimeException()
            val viewModel = viewModel(incomeRepository = incomeRepo)
            collect(viewModel)

            assertEquals(
                DashboardUiState.Error(genericErrorText),
                viewModel.uiState.value,
            )
        }

    @Test
    fun onRetry_afterClearingObserveIncomeError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository(listOf(income(id = "i1", amountCents = 1_000L, date = daysAgo(1))))
            incomeRepo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val viewModel = viewModel(incomeRepository = incomeRepo)
            collect(viewModel)
            assertEquals(DashboardUiState.Error(genericErrorText), viewModel.uiState.value)

            incomeRepo.nextObserveIncomeError = null
            viewModel.onRetry()
            advanceUntilIdle()

            assertEquals(1_000L, viewModel.content().incomeCents)
        }
}
