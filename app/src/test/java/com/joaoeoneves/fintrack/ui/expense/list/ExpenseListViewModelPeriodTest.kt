package com.joaoeoneves.fintrack.ui.expense.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.encode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Period behavior of [ExpenseListViewModel]: the route's "period" token seeds the initial period,
 * user selections are stored under "selectedPeriod" and win over the route value on recreation,
 * and month navigation re-queries the repository. Robolectric is needed for the SavedStateHandle `Bundle` (see
 * [ExpenseListViewModelTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpenseListViewModelPeriodTest {
    private val testDispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun noonOn(
        year: Int,
        month: Int,
        day: Int,
    ): Instant = LocalDate.of(year, month, day).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC)

    private fun item(
        id: String,
        date: Instant,
    ) = Expense(id = id, name = id, amountCents = 100L, category = BuiltInCategories.SHOPPING, date = date, note = null)

    private val aug = item("aug", noonOn(2026, 8, 15))
    private val sep = item("sep", noonOn(2026, 9, 15))
    private val oct = item("oct", noonOn(2026, 10, 15))
    private val repo = FakeExpenseRepository(listOf(aug, sep, oct))

    private fun handle(token: String) = SavedStateHandle(mapOf("period" to token))

    private fun viewModel(
        handle: SavedStateHandle,
        clock: Clock = this.clock,
    ) = ExpenseListViewModel(repo, FakeCategoryRepository(), handle, clock)

    private fun TestScope.content(vm: ExpenseListViewModel): ExpenseListUiState.Content {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value as ExpenseListUiState.Content
    }

    private fun ExpenseListViewModel.current() = uiState.value as ExpenseListUiState.Content

    @Test
    fun routeToken_seedsEachPeriodVariant() =
        runTest(testDispatcher) {
            val cases =
                mapOf(
                    Period.CurrentMonth to setOf(oct),
                    Period.Month(YearMonth.of(2026, 9)) to setOf(sep),
                    Period.Rolling(TimeRange.THREE_MONTHS) to setOf(aug, sep, oct),
                    Period.Custom(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15)) to setOf(aug, sep),
                )
            for ((period, expected) in cases) {
                val content = content(viewModel(handle(period.encode())))
                assertEquals(period, content.period)
                assertEquals(period.toString(), expected, content.expenses.toSet())
                assertEquals(period is Period.Month, content.canGoToNextMonth)
            }
        }

    @Test
    fun missingPeriodArgument_defaultsToCurrentMonth() =
        runTest(testDispatcher) {
            val content = content(viewModel(SavedStateHandle()))

            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(listOf(oct), content.expenses)
            assertFalse(content.canGoToNextMonth)
        }

    @Test
    fun missingPeriodArgument_stillAllowsMonthNavigation() =
        runTest(testDispatcher) {
            val vm = viewModel(SavedStateHandle())
            content(vm)

            vm.onPreviousMonth()
            advanceUntilIdle()

            assertEquals(Period.Month(YearMonth.of(2026, 9)), vm.current().period)
            assertEquals(listOf(sep), vm.current().expenses)
        }

    @Test
    fun badRouteToken_fallsBackToCurrentMonth() =
        runTest(testDispatcher) {
            for (token in listOf("", "nonsense", "rolling:ONE_WEEK", "month:2099-01")) {
                val content = content(viewModel(handle(token)))
                assertEquals(token, Period.CurrentMonth, content.period)
                assertEquals(listOf(oct), content.expenses)
            }
        }

    @Test
    fun previousAndNext_reQueryRepository() =
        runTest(testDispatcher) {
            val vm = viewModel(handle("current"))
            content(vm)

            vm.onPreviousMonth()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 9)), vm.current().period)
            assertEquals(listOf(sep), vm.current().expenses)
            assertTrue(vm.current().canGoToNextMonth)

            vm.onNextMonth()
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.current().period)
            assertEquals(listOf(oct), vm.current().expenses)
            assertFalse(vm.current().canGoToNextMonth)
        }

    @Test
    fun onPeriodSelected_switchesToRollingAndCustom() =
        runTest(testDispatcher) {
            val vm = viewModel(handle("current"))
            content(vm)

            vm.onPeriodSelected(Period.Rolling(TimeRange.ONE_YEAR))
            advanceUntilIdle()
            assertEquals(setOf(aug, sep, oct), vm.current().expenses.toSet())

            vm.onPeriodSelected(Period.Custom(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)))
            advanceUntilIdle()
            assertEquals(listOf(oct), vm.current().expenses)
        }

    @Test
    fun userSelection_isStoredSeparately_andRouteArgumentIsNotOverwritten() =
        runTest(testDispatcher) {
            val savedState = handle("rolling:SIX_MONTHS")
            val vm = viewModel(savedState)
            content(vm)

            vm.onPeriodSelected(Period.Month(YearMonth.of(2026, 8)))
            advanceUntilIdle()

            assertEquals("rolling:SIX_MONTHS", savedState.get<String>("period"))
            assertEquals("month:2026-08", savedState.get<String>("selectedPeriod"))
        }

    @Test
    fun userSelection_survivesRecreation_andWinsOverRouteValue() =
        runTest(testDispatcher) {
            val savedState = handle("rolling:THREE_MONTHS")
            val original = viewModel(savedState)
            content(original)
            original.onPeriodSelected(Period.CurrentMonth)
            original.onPreviousMonth()
            advanceUntilIdle()

            val recreated = content(viewModel(savedState))

            assertEquals(Period.Month(YearMonth.of(2026, 9)), recreated.period)
            assertEquals(listOf(sep), recreated.expenses)
        }

    @Test
    fun currentMonth_afterRolloverAndRetry_resolvesNewMonth() =
        runTest(testDispatcher) {
            val mutableClock = MutableClock(now, ZoneOffset.UTC)
            val nov = item("nov", noonOn(2026, 11, 1))
            repo.addExpense(nov)
            val vm = viewModel(handle("current"), mutableClock)
            assertEquals(listOf(oct), content(vm).expenses)

            mutableClock.now = Instant.parse("2026-11-01T13:00:00Z")
            vm.onRetry()
            advanceUntilIdle()

            assertEquals(Period.CurrentMonth, vm.current().period)
            assertEquals(listOf(nov), vm.current().expenses)
        }
}
