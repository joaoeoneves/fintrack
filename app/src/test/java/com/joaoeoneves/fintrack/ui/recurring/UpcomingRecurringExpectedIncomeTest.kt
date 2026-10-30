package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.testutil.MutableClock
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** The read-only "Expected income" group of [UpcomingRecurringViewModel]: receiving happens in the Income tab. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpcomingRecurringExpectedIncomeTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)
    private val salary = PlannedIncome("p1", "Salary", 150_000L, 0, startMonth = YearMonth.of(2026, 10))
    private val plan = FakePlannedIncomeRepository(listOf(salary))
    private val incomes = FakeIncomeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(plannedIncome: FakePlannedIncomeRepository = plan): UpcomingRecurringViewModel {
        val recurring = FakeRecurringRepository()
        val months = FakeMonthSettingsRepository()
        return UpcomingRecurringViewModel(
            ObserveUpcomingRecurring(recurring),
            ObserveExpectedIncome(plannedIncome, months, incomes),
            clock,
        )
    }

    private fun TestScope.content(vm: UpcomingRecurringViewModel): UpcomingRecurringUiState.Content {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value as UpcomingRecurringUiState.Content
    }

    @Test
    fun insideTheWindow_listsTheUnreceivedRows() =
        runTest(dispatcher) {
            val expected = content(viewModel()).expectedIncome!!

            assertEquals(YearMonth.of(2026, 10), expected.month)
            assertEquals(LocalDate.of(2026, 10, 30), expected.payday)
            assertEquals(listOf("p1"), expected.rows.map { it.lines.single().id })
        }

    @Test
    fun outsideTheWindow_hasNoRows() =
        runTest(dispatcher) {
            clock.now = Instant.parse("2026-10-10T10:00:00Z")

            assertTrue(content(viewModel()).expectedIncome!!.rows.isEmpty())
        }

    @Test
    fun failingExpectedIncome_leavesTheUpcomingListIntact() =
        runTest(dispatcher) {
            val broken = FakePlannedIncomeRepository(nextObserveError = IllegalStateException("boom"))

            val state = content(viewModel(broken))

            assertNull(state.expectedIncome)
            assertNotNull(state.overview)
        }
}
