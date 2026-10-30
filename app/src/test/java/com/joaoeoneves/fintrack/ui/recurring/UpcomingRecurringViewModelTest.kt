package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.usecase.ObserveExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpcomingRecurringViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Due today: day 15, cursor at the previous month's occurrence.
    private val rent = recurringItem(id = "rent", name = "Rent", amountCents = 1_000, handledThrough = day(2026, 2, 15))

    private fun viewModel(repo: FakeRecurringRepository) =
        UpcomingRecurringViewModel(
            ObserveUpcomingRecurring(repo),
            ObserveExpectedIncome(FakePlannedIncomeRepository(), FakeMonthSettingsRepository(), FakeIncomeRepository()),
            clock,
        )

    private fun TestScope.started(vm: UpcomingRecurringViewModel): UpcomingRecurringUiState {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value
    }

    private fun content(state: UpcomingRecurringUiState) = state as UpcomingRecurringUiState.Content

    @Test
    fun startsLoading_thenShowsRowsAndTotals() =
        runTest(testDispatcher) {
            val salary =
                recurringItem(
                    id = "sal",
                    type = RecurringType.INCOME,
                    amountCents = 5_000,
                    startDate = day(2026, 3, 1),
                )
            val vm = viewModel(FakeRecurringRepository(listOf(rent, salary)))
            assertEquals(UpcomingRecurringUiState.Loading, vm.uiState.value)

            val state = content(started(vm))

            assertEquals(day(2026, 3, 15), state.today)
            assertEquals(12_000L, state.overview.annualExpenseCents)
            assertTrue(state.overview.upcoming.any { it.item.id == "rent" && it.isDue })
        }

    @Test
    fun empty_hasNoItems() =
        runTest(testDispatcher) {
            val state = content(started(viewModel(FakeRecurringRepository())))

            assertEquals(false, state.overview.hasItems)
            assertTrue(state.overview.upcoming.isEmpty())
        }

    @Test
    fun observing_isReadOnly_noPostingNoCursorMove() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(rent))
            val vm = viewModel(repo)

            val row = content(started(vm)).overview.upcoming.single()

            assertTrue(row.isDue)
            assertTrue(repo.confirmedTransactions.isEmpty())
            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
            assertTrue(repo.items.single().skipped.isEmpty())
            val actions = vm.javaClass.declaredMethods.map { it.name }
            assertTrue(actions.none { it.startsWith("onConfirm") || it.startsWith("onSkip") || it.startsWith("onUndo") })
        }

    @Test
    fun observeFailure_showsError_andRetryAfterClockChangeRecomputesToday() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(rent), nextObserveError = IllegalStateException("down"))
            val vm = viewModel(repo)
            assertTrue(started(vm) is UpcomingRecurringUiState.Error)

            repo.nextObserveError = null
            clock.now = Instant.parse("2026-03-16T10:00:00Z")
            vm.onRetry()
            advanceUntilIdle()

            val state = content(vm.uiState.value)
            assertEquals(day(2026, 3, 16), state.today)
            assertEquals(1, state.overview.upcoming.size)
        }

