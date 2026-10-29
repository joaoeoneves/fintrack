package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.ObserveExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.domain.usecase.UndoRecurringOccurrence
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** The "Expected income" group of [UpcomingRecurringViewModel] and its Received action. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpcomingRecurringExpectedIncomeTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)
    private val plan = FakePlannedIncomeRepository(listOf(PlannedIncome("p1", "Salary", 150_000L, 0)))
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
            HandleRecurringOccurrence(recurring, FakeCategoryRepository()),
            RecordPayday(
                plannedIncome,
                months,
                incomes,
                FakeAutomationSettingsRepository(),
                FakeSalaryLineLabelsProvider(),
            ),
            UndoRecurringOccurrence(recurring, FakeExpenseRepository()),
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
    fun onReceiveIncome_recordsIt_andTheRowDisappears() =
        runTest(dispatcher) {
            val vm = viewModel()
            val row = content(vm).expectedIncome!!.rows.single()

            vm.onReceiveIncome(row, LocalDate.of(2026, 10, 27), null)
            advanceUntilIdle()

            val stored = incomes.getAllIncome().getOrThrow().single()
            assertEquals("pay_p1_202610", stored.id)
            assertEquals(150_000L, stored.amountCents)
            assertEquals(Instant.parse("2026-10-27T12:00:00Z"), stored.date)
            assertTrue(content(vm).expectedIncome!!.rows.isEmpty())
        }

    @Test
    fun onReceiveIncome_withAmountOverride() =
        runTest(dispatcher) {
            val vm = viewModel()
            val row = content(vm).expectedIncome!!.rows.single()

            vm.onReceiveIncome(row, LocalDate.of(2026, 10, 28), 140_000L)
            advanceUntilIdle()

            assertEquals(
                140_000L,
                incomes
                    .getAllIncome()
                    .getOrThrow()
                    .single()
                    .amountCents,
            )
        }

    @Test
    fun onReceiveIncome_futureDate_emitsAnError_andWritesNothing() =
        runTest(dispatcher) {
            val vm = viewModel()
            val errors = mutableListOf<UiText>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors += it } }
            val row = content(vm).expectedIncome!!.rows.single()

            vm.onReceiveIncome(row, LocalDate.of(2026, 10, 29), null)
            advanceUntilIdle()

            assertEquals(1, errors.size)
            assertTrue(incomes.getAllIncome().getOrThrow().isEmpty())
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
