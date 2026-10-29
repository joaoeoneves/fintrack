package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
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
import java.time.YearMonth

/** Days off in [IncomeTabViewModel]: `onSetWorkingDays(month, days, daysOff)` and the resulting state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelDaysOffTest : IncomeTabViewModelTestBase() {
    private val oct = YearMonth.of(2026, 10)

    private suspend fun kotlinx.coroutines.test.TestScope.perDayLine() {
        incomeRepo.savePlannedIncome(PlannedIncome("m", "Meal", 1046L, 0, basis = IncomeBasis.PER_WORKING_DAY))
        advanceUntilIdle()
    }

    @Test
    fun noSettings_daysOffIsZero() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(0, vm.content().workingDays.daysOff)
        }

    @Test
    fun setDaysOff_subtractsFromCalendarDays_andRecomputesPerDayIncome() =
        runTest(testDispatcher) {
            perDayLine()
            val vm = viewModel()
            collect(vm)

            vm.onSetWorkingDays(oct, null, daysOff = 3)
            advanceUntilIdle()

            val state = vm.content().workingDays
            assertEquals(3, state.daysOff)
            assertEquals(18, state.days)
            assertEquals(21, state.calendarDays)
            assertFalse(state.overridden)
            assertEquals(1046L * 18, vm.content().summary.expectedIncomeCents)
            assertEquals(listOf(MonthSettings(oct, null, 3)), monthSettingsRepo.settings)
        }

    @Test
    fun setOverrideAndDaysOffTogether_overrideWins_bothStored() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onSetWorkingDays(oct, 15, daysOff = 2)
            advanceUntilIdle()

            val state = vm.content().workingDays
            assertEquals(15, state.days)
            assertEquals(2, state.daysOff)
            assertTrue(state.overridden)
            assertEquals(listOf(MonthSettings(oct, 15, 2)), monthSettingsRepo.settings)
        }

    @Test
    fun nullDaysOff_leavesExistingDaysOffUntouched() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onSetWorkingDays(oct, null, daysOff = 4)
            advanceUntilIdle()

            vm.onSetWorkingDays(oct, 12)
            advanceUntilIdle()

            assertEquals(listOf(MonthSettings(oct, 12, 4)), monthSettingsRepo.settings)
        }

    @Test
    fun zeroDaysOff_clearsThem_andKeepsOverride() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onSetWorkingDays(oct, 12, daysOff = 4)
            advanceUntilIdle()

            vm.onSetWorkingDays(oct, 12, daysOff = 0)
            advanceUntilIdle()

            assertEquals(0, vm.content().workingDays.daysOff)
            assertEquals(listOf(MonthSettings(oct, 12, 0)), monthSettingsRepo.settings)
        }

    @Test
    fun clearingOverrideAndDaysOff_removesTheMonthSettings() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onSetWorkingDays(oct, 12, daysOff = 4)
            advanceUntilIdle()

            vm.onSetWorkingDays(oct, null, daysOff = 0)
            advanceUntilIdle()

            assertTrue(monthSettingsRepo.settings.isEmpty())
            assertEquals(21, vm.content().workingDays.days)
        }

    @Test
    fun daysOffBeyondCalendar_clampDaysAtZero() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onSetWorkingDays(oct, null, daysOff = 23)
            advanceUntilIdle()

            assertEquals(0, vm.content().workingDays.days)
        }
}
