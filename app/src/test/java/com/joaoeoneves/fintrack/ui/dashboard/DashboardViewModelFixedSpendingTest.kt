package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [DashboardUiState.Content.fixedSpendingCents]: spending that came from recurring items. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelFixedSpendingTest : DashboardViewModelTestBase() {
    private fun viewModelWith(expenses: List<Expense>) =
        viewModel(
            expenseRepository = FakeExpenseRepository(expenses),
            budgetRepository = FakeBudgetRepository(),
            incomeRepository = FakeIncomeRepository(),
        )

    @Test
    fun noExpenses_fixedIsZero() =
        runTest(testDispatcher) {
            val viewModel = viewModelWith(emptyList())
            collect(viewModel)

            assertEquals(0L, viewModel.content().fixedSpendingCents)
        }

    @Test
    fun recurringOccurrences_areFixed_restIsVariable() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "rec_r1_20261001", amountCents = 50_000L, date = daysAgo(3)),
                    expense(id = "manual", amountCents = 7_000L, date = daysAgo(2)),
                )
            val viewModel = viewModelWith(expenses)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(50_000L, content.fixedSpendingCents)
            assertEquals(57_000L, content.spendingCents)
        }

    @Test
    fun refundOfRecurring_isNetted_andSavingKindExcluded() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "rec_r1_20261001", amountCents = 50_000L, date = daysAgo(3)),
                    expense(id = "rec_r1_20261002", amountCents = 10_000L, date = daysAgo(2)).copy(isRefund = true),
                    expense(
                        id = "rec_r2_20261001",
                        amountCents = 20_000L,
                        category = BuiltInCategories.INVESTMENTS,
                        date = daysAgo(2),
                    ),
                )
            val viewModel = viewModelWith(expenses)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(40_000L, content.fixedSpendingCents)
            assertEquals(20_000L, content.savedCents)
        }
}
