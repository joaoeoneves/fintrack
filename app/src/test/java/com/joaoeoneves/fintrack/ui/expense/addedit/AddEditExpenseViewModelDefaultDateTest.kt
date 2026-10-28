package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** The `period` route argument of [AddEditExpenseViewModel]: a new expense defaults to a past month's last day. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelDefaultDateTest {
    private val testDispatcher = StandardTestDispatcher()
    private val zone = ZoneId.systemDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        vararg args: Pair<String, Any?>,
        repository: FakeExpenseRepository = FakeExpenseRepository(),
    ) = AddEditExpenseViewModel(
        repository,
        FakeCategoryRepository(),
        deleteTransactionFor(repository),
        SavedStateHandle(mapOf(*args)),
    )

    private fun AddEditExpenseViewModel.date(): Instant = (uiState.value as AddEditUiState.Ready).form.date

    @Test
    fun pastMonth_defaultsToItsLastDayAtNoon() =
        runTest(testDispatcher) {
            val month = YearMonth.now(zone).minusMonths(2)
            val vm = viewModel("period" to "month:$month")
            advanceUntilIdle()

            assertEquals(
                month
                    .atEndOfMonth()
                    .atTime(LocalTime.NOON)
                    .atZone(zone)
                    .toInstant(),
                vm.date(),
            )
        }

    @Test
    fun noPeriod_defaultsToNow() =
        runTest(testDispatcher) {
            val before = Instant.now()
            val vm = viewModel()
            advanceUntilIdle()

            assertTrue(Duration.between(before, vm.date()).abs() < Duration.ofSeconds(5))
        }

    @Test
    fun currentMonthToken_defaultsToNow() =
        runTest(testDispatcher) {
            val before = Instant.now()
            val vm = viewModel("period" to "month:${YearMonth.now(zone)}")
            advanceUntilIdle()

            assertTrue(Duration.between(before, vm.date()).abs() < Duration.ofSeconds(5))
        }

    @Test
    fun garbagePeriod_defaultsToNow() =
        runTest(testDispatcher) {
            val before = Instant.now()
            val vm = viewModel("period" to "not-a-period")
            advanceUntilIdle()

            assertTrue(Duration.between(before, vm.date()).abs() < Duration.ofSeconds(5))
        }

    @Test
    fun editMode_ignoresThePeriod_andKeepsTheExpenseDate() =
        runTest(testDispatcher) {
            val stored = Instant.parse("2026-03-10T08:00:00Z")
            val expense = testExpense("e1", 250L, stored)

            val repo = FakeExpenseRepository(listOf(expense))
            val month = YearMonth.now(zone).minusMonths(2)
            val vm = viewModel("expenseId" to "e1", "period" to "month:$month", repository = repo)
            advanceUntilIdle()

            assertEquals(stored, vm.date())
        }
}
