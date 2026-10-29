package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.testIncome
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

/** The `period` route argument of [AddEditIncomeViewModel]: a new income defaults to a past month's last day. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelDefaultDateTest {
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
        repository: FakeIncomeRepository = FakeIncomeRepository(),
    ) = AddEditIncomeViewModel(
        repository,
        deleteTransactionFor(incomes = repository),
        SavedStateHandle(mapOf(*args)),
        java.time.Clock.systemDefaultZone(),
    )

    private fun AddEditIncomeViewModel.date(): Instant = (uiState.value as AddEditIncomeUiState.Ready).form.date

    @Test
    fun pastMonth_defaultsToItsLastDayAtNoon() =
        runTest(testDispatcher) {
            val month = YearMonth.now(zone).minusMonths(3)
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
    fun editMode_ignoresThePeriod_andKeepsTheIncomeDate() =
        runTest(testDispatcher) {
            val stored = Instant.parse("2026-03-10T08:00:00Z")
            val repo = FakeIncomeRepository(listOf(testIncome("i1", 1_000L, stored)))
            val month = YearMonth.now(zone).minusMonths(3)
            val vm = viewModel("incomeId" to "i1", "period" to "month:$month", repository = repo)
            advanceUntilIdle()

            assertEquals(stored, vm.date())
        }
}
