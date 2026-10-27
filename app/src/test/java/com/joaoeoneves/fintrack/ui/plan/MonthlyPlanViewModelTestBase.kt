package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.robolectric.annotation.Config

/** Shared setup for the [MonthlyPlanViewModel] tests. */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = Application::class)
open class MonthlyPlanViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val incomeRepo = FakePlannedIncomeRepository()
    protected val budgetRepo = FakeBudgetRepository()
    protected val categoryRepo = FakeCategoryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun viewModel() = MonthlyPlanViewModel(incomeRepo, budgetRepo, categoryRepo)

    protected fun TestScope.collect(viewModel: MonthlyPlanViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    protected fun MonthlyPlanViewModel.content() = uiState.value as MonthlyPlanUiState.Content

    protected fun line(
        id: String,
        amountCents: Long = 100_000L,
        order: Int = 0,
    ) = PlannedIncome(id = id, label = id, amountCents = amountCents, order = order)
}
