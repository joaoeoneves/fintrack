package com.joaoeoneves.fintrack.ui.income.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * [IncomeListViewModel] note/amount query matching and the guarantee that only period/retry
 * changes re-subscribe the repository. Robolectric for the `toRoute` / `Bundle` reason documented
 * on [IncomeListViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeListViewModelFilterTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val salary =
        Income(id = "s", source = "Salary", amountCents = 250_000L, date = now.minus(2, ChronoUnit.DAYS), note = "pay")
    private val gift =
        Income(id = "g", source = "Gift", amountCents = 5_050L, date = now.minus(1, ChronoUnit.DAYS), note = null)

    private class CountingIncomeRepository(
        private val delegate: FakeIncomeRepository,
    ) : IncomeRepository by delegate {
        var observeCalls = 0

        override fun observeIncome(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Income>> {
            observeCalls++
            return delegate.observeIncome(startInclusive, endExclusive)
        }
    }

    private fun viewModel(repository: IncomeRepository) =
        IncomeListViewModel(repository, SavedStateHandle(mapOf("period" to "current")), clock)

    private fun TestScope.collect(vm: IncomeListViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun IncomeListViewModel.content() = uiState.value as IncomeListUiState.Content

    @Test
    fun query_matchesNote() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeIncomeRepository(listOf(salary, gift)))
            collect(vm)

            vm.onQueryChanged("PAY")
            advanceUntilIdle()

            assertEquals(listOf(salary), vm.content().income)
        }

    @Test
    fun query_matchesAmount() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeIncomeRepository(listOf(salary, gift)))
            collect(vm)

            vm.onQueryChanged("50,50")
            advanceUntilIdle()

            assertEquals(listOf(gift), vm.content().income)
        }

    @Test
    fun changingQueryOrSort_doesNotReSubscribeRepository_butRetryDoes() =
        runTest(testDispatcher) {
            val repo = CountingIncomeRepository(FakeIncomeRepository(listOf(salary, gift)))
            val vm = viewModel(repo)
            collect(vm)
            assertEquals(1, repo.observeCalls)

            vm.onQueryChanged("g")
            advanceUntilIdle()
            vm.onQueryChanged("gi")
            advanceUntilIdle()
            vm.onSortSelected(IncomeSortOption.AMOUNT_ASC)
            advanceUntilIdle()

            assertEquals(1, repo.observeCalls)
            assertEquals(listOf(gift), vm.content().income)

            vm.onRetry()
            advanceUntilIdle()
            assertEquals(2, repo.observeCalls)
        }
}
