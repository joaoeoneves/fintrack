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
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.ObserveExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.domain.usecase.UndoRecurringOccurrence
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Undoing a receive, a confirm or a skip from [UpcomingRecurringViewModel] only takes back what really happened. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpcomingRecurringUndoTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)
    private val salary = PlannedIncome("p1", "Salary", 150_000L, 0, startMonth = YearMonth.of(2026, 10))
    private val plan = FakePlannedIncomeRepository(listOf(salary))
    private val incomes = FakeIncomeRepository()
    private val recurring = FakeRecurringRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): UpcomingRecurringViewModel {
        val months = FakeMonthSettingsRepository()
        return UpcomingRecurringViewModel(
            ObserveUpcomingRecurring(recurring),
            ObserveExpectedIncome(plan, months, incomes),
            HandleRecurringOccurrence(recurring, FakeCategoryRepository()),
            RecordPayday(plan, months, incomes, FakeAutomationSettingsRepository(), FakeSalaryLineLabelsProvider()),
            UndoRecurringOccurrence(recurring, FakeExpenseRepository(), incomes),
            clock,
        )
    }

    private fun TestScope.content(vm: UpcomingRecurringViewModel): UpcomingRecurringUiState.Content {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value as UpcomingRecurringUiState.Content
    }

    @Test
    fun undoReceive_calledBeforeTheReceiveRan_waitsForItAndThenRemovesIt() =
        runTest(dispatcher) {
            val vm = viewModel()
            val row = content(vm).expectedIncome!!.rows.single()

            // Both are queued on the dispatcher: the undo must not run ahead of the receive.
            val undo = vm.onReceiveIncome(row, LocalDate.of(2026, 10, 27), null)
            undo()
            advanceUntilIdle()

            assertTrue(incomes.getAllIncome().getOrThrow().isEmpty())
        }

    @Test
    fun undoReceive_afterAFailedReceive_leavesAnExistingIncomeAlone() =
        runTest(dispatcher) {
            val existing = Income("pay_p1_202610", "Salary", 140_000L, Instant.parse("2026-10-20T12:00:00Z"), null)
            val vm = viewModel()
            val row = content(vm).expectedIncome!!.rows.single()
            incomes.addIncome(existing)

            // A future date makes the receive fail, so nothing was written and nothing may be undone.
            val undo = vm.onReceiveIncome(row, LocalDate.of(2026, 10, 29), null)
            advanceUntilIdle()
            undo()
            advanceUntilIdle()

            assertEquals(listOf(existing), incomes.getAllIncome().getOrThrow())
        }

    @Test
    fun undoConfirm_whenTheCursorMovedPast_keepsTheLaterCursor() =
        runTest(dispatcher) {
            val rent = recurringItem(id = "rent", handledThrough = day(2026, 10, 15))
            recurring.setItems(listOf(rent))
            val vm = viewModel()

            // The undo of the September confirm arrives after October was handled.
            vm.onUndo(RecurringUndo(RecurringUndo.Kind.CONFIRMED, "rent", "Rent", day(2026, 9, 15), day(2026, 8, 15)))
            advanceUntilIdle()

            assertEquals(day(2026, 10, 15), recurring.items.single().handledThrough)
        }

    @Test
    fun undoSkip_whenTheCursorMovedPast_keepsTheLaterCursor_butUnskips() =
        runTest(dispatcher) {
            val skipped = setOf(day(2026, 9, 15))
            val rent = recurringItem(id = "rent", handledThrough = day(2026, 10, 15), skipped = skipped)
            recurring.setItems(listOf(rent))
            val vm = viewModel()

            vm.onUndo(RecurringUndo(RecurringUndo.Kind.SKIPPED, "rent", "Rent", day(2026, 9, 15), day(2026, 8, 15)))
            advanceUntilIdle()

            assertEquals(day(2026, 10, 15), recurring.items.single().handledThrough)
            assertTrue(
                recurring.items
                    .single()
                    .skipped
                    .isEmpty(),
            )
        }
}
