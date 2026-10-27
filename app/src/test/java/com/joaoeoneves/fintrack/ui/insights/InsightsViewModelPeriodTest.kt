package com.joaoeoneves.fintrack.ui.insights

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.encode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Period selection of [InsightsViewModel]: months, rolling windows, custom ranges and the route argument. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InsightsViewModelPeriodTest : InsightsViewModelTestBase() {
    private fun twoMonthRepo() =
        FakeExpenseRepository(
            listOf(
                expense(id = "sep", amountCents = 9_000L, date = noonOn(previousMonth, 15)),
                expense(id = "oct", amountCents = 1_500L, date = noonOn(currentMonth, 5)),
            ),
        )

    @Test
    fun initialState_isLoading() =
        runTest(testDispatcher) {
            assertEquals(InsightsUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun default_isCurrentMonth_withoutNext() =
        runTest(testDispatcher) {
            val vm = viewModel(twoMonthRepo())
            collect(vm)

            val content = vm.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertFalse(content.canGoToNextMonth)
            assertEquals(1_500L, content.total(BuiltInCategories.SHOPPING))
        }

    @Test
    fun previousMonth_switchesData_andAllowsGoingForward() =
        runTest(testDispatcher) {
            val vm = viewModel(twoMonthRepo())
            collect(vm)

            vm.onPreviousMonth()
            advanceUntilIdle()

            val content = vm.content()
            assertEquals(Period.Month(previousMonth), content.period)
            assertTrue(content.canGoToNextMonth)
            assertTrue(content.breakdown.hasExpenses)
            assertEquals(9_000L, content.breakdown.categoryTotals.sumOf { it.totalCents })
        }

    @Test
    fun nextMonth_fromPastMonth_returnsToCurrentMonth_andNeverPastIt() =
        runTest(testDispatcher) {
            val vm = viewModel(twoMonthRepo())
            collect(vm)
            vm.onPreviousMonth()
            advanceUntilIdle()

            vm.onNextMonth()
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)

            vm.onNextMonth()
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)
        }

    @Test
    fun rollingRange_spansBothMonths() =
        runTest(testDispatcher) {
            val vm = viewModel(twoMonthRepo())
            collect(vm)

            vm.onPeriodSelected(Period.Rolling(TimeRange.THREE_MONTHS))
            advanceUntilIdle()

            val content = vm.content()
            assertEquals(Period.Rolling(TimeRange.THREE_MONTHS), content.period)
            assertFalse(content.canGoToNextMonth)
            assertEquals(10_500L, content.breakdown.categoryTotals.sumOf { it.totalCents })
        }

    @Test
    fun rollingRange_oneYear_includesOlderExpense() =
        runTest(testDispatcher) {
            val old = expense(id = "old", amountCents = 700L, date = noonOn(currentMonth.minusMonths(8), 10))
            val vm = viewModel(FakeExpenseRepository(listOf(old, expense(id = "now", amountCents = 300L))))
            collect(vm)

            vm.onPeriodSelected(Period.Rolling(TimeRange.THREE_MONTHS))
            advanceUntilIdle()
            assertEquals(
                300L,
                vm
                    .content()
                    .breakdown.categoryTotals
                    .sumOf { it.totalCents },
            )

            vm.onPeriodSelected(Period.Rolling(TimeRange.ONE_YEAR))
            advanceUntilIdle()
            assertEquals(
                1_000L,
                vm
                    .content()
                    .breakdown.categoryTotals
                    .sumOf { it.totalCents },
            )
        }

    @Test
    fun customRange_inclusiveBothEnds() =
        runTest(testDispatcher) {
            val onStart = expense(id = "start", amountCents = 100L, date = noonOn(previousMonth, 10))
            val onEnd = expense(id = "end", amountCents = 200L, date = noonOn(previousMonth, 20))
            val before = expense(id = "before", amountCents = 400L, date = noonOn(previousMonth, 9))
            val after = expense(id = "after", amountCents = 800L, date = noonOn(previousMonth, 21))
            val vm = viewModel(FakeExpenseRepository(listOf(onStart, onEnd, before, after)))
            collect(vm)

            val custom = Period.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20))
            vm.onPeriodSelected(custom)
            advanceUntilIdle()

            val content = vm.content()
            assertEquals(custom, content.period)
            assertFalse(content.canGoToNextMonth)
            assertEquals(300L, content.breakdown.categoryTotals.sumOf { it.totalCents })
        }

    @Test
    fun selectingCurrentMonthAsMonth_isNormalizedToCurrentMonth() =
        runTest(testDispatcher) {
            val vm = viewModel(twoMonthRepo())
            collect(vm)

            vm.onPeriodSelected(Period.Month(currentMonth))
            advanceUntilIdle()

            assertEquals(Period.CurrentMonth, vm.content().period)
        }

    @Test
    fun routeArgument_setsInitialPeriod() =
        runTest(testDispatcher) {
            val vm =
                viewModel(
                    twoMonthRepo(),
                    savedStateHandle =
                        SavedStateHandle(
                            mapOf(
                                "period" to Period.Rolling(TimeRange.THREE_MONTHS).encode(),
                            ),
                        ),
                )
            collect(vm)

            assertEquals(Period.Rolling(TimeRange.THREE_MONTHS), vm.content().period)
        }

    @Test
    fun laterSelection_overridesRouteArgument_andSurvivesRecreationWithSameHandle() =
        runTest(testDispatcher) {
            val handle = SavedStateHandle(mapOf("period" to "current"))
            val original = viewModel(twoMonthRepo(), savedStateHandle = handle)
            collect(original)
            original.onPeriodSelected(Period.Month(previousMonth))
            advanceUntilIdle()

            val recreated = viewModel(twoMonthRepo(), savedStateHandle = handle)
            collect(recreated)

            assertEquals(Period.Month(previousMonth), recreated.content().period)
        }

    @Test
    fun previousNext_onRollingPeriod_areNoOps() =
        runTest(testDispatcher) {
            val vm = viewModel(twoMonthRepo())
            collect(vm)
            vm.onPeriodSelected(Period.Rolling(TimeRange.SIX_MONTHS))
            advanceUntilIdle()

            vm.onPreviousMonth()
            vm.onNextMonth()
            advanceUntilIdle()

            assertEquals(Period.Rolling(TimeRange.SIX_MONTHS), vm.content().period)
        }
}
