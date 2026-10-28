package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Error handling of the expense feed behind [MonthlyPlanViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelFailureTest : MonthlyPlanViewModelTestBase() {
    @Test
    fun expensesFailure_surfacesErrorState() =
        runTest(testDispatcher) {
            val failing = FakeExpenseRepository(nextObserveExpensesError = IllegalStateException("boom"))
            val vm = viewModel(expenses = failing)
            collect(vm)

            assertTrue(vm.uiState.value is MonthlyPlanUiState.Error)
        }
}
