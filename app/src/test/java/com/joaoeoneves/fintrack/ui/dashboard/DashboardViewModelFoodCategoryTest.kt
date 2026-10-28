package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BuiltInCategories.FOOD] on the dashboard: it is a SPENDING category (never counted as saved).
 * Its category total and budget are covered by the Activity and Plan tests. See
 * [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelFoodCategoryTest : DashboardViewModelTestBase() {
    /** One expense per non-FOOD category plus two FOOD expenses, all in the current month. */
    private fun mixedRepo() =
        FakeExpenseRepository(
            listOf(
                expense(id = "f1", amountCents = 2_500L, category = BuiltInCategories.FOOD, date = daysAgo(1)),
                expense(id = "f2", amountCents = 1_000L, category = BuiltInCategories.FOOD, date = daysAgo(2)),
                expense(id = "s1", amountCents = 7_000L, category = BuiltInCategories.SHOPPING, date = daysAgo(3)),
                expense(id = "t1", amountCents = 300L, category = BuiltInCategories.TRANSFER, date = daysAgo(4)),
                expense(id = "r1", amountCents = 9_000L, category = BuiltInCategories.RECURRING, date = daysAgo(5)),
                expense(id = "i1", amountCents = 50_000L, category = BuiltInCategories.INVESTMENTS, date = daysAgo(6)),
            ),
        )

    @Test
    fun foodExpense_countsAsSpending_notSaved() =
        runTest(testDispatcher) {
            val food = expense(id = "f1", amountCents = 1_234L, category = BuiltInCategories.FOOD, date = daysAgo(1))
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(food)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(1_234L, content.spendingCents)
            assertEquals(0L, content.savedCents)
            assertEquals(1_234L, content.outflowCents)
        }

    @Test
    fun mixedCategories_foodAddedToSpending() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = mixedRepo())
            collect(viewModel)

            val content = viewModel.content()
            // FOOD + SHOPPING + TRANSFER + RECURRING
            assertEquals(3_500L + 7_000L + 300L + 9_000L, content.spendingCents)
            assertEquals(50_000L, content.savedCents)
        }
}
