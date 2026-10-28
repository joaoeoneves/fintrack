package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeBuiltInCategoryNames
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.MoveCategoryItems
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** Shared setup and helpers for the [CategoriesViewModel] test classes. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class CategoriesViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val expenses = FakeExpenseRepository()
    protected val budgets = FakeBudgetRepository()
    protected val recurring = FakeRecurringRepository()
    protected val errors = mutableListOf<UiText>()

    protected val saveFailed = UiText.StringResource(R.string.error_category_save_failed)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected val groups = FakeEnvelopeRepository()

    protected fun newViewModel(
        repo: FakeCategoryRepository,
        groupRepo: EnvelopeRepository = groups,
    ): CategoriesViewModel =
        CategoriesViewModel(
            repo,
            CheckCategoryUsage(expenses, budgets, recurring),
            FakeBuiltInCategoryNames(),
            MoveCategoryItems(expenses, budgets, recurring, testDispatcher),
            groupRepo,
        )

    /** Creates the view model, collects its state and events, and lets the first emission land. */
    protected fun TestScope.start(
        repo: FakeCategoryRepository,
        groupRepo: EnvelopeRepository = groups,
    ): CategoriesViewModel {
        val viewModel = newViewModel(repo, groupRepo)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        val unconfined = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(unconfined) { viewModel.errorEvent.collect { errors += it } }
        advanceUntilIdle()
        return viewModel
    }

    protected fun CategoriesViewModel.content() = uiState.value as CategoriesUiState.Content
}
