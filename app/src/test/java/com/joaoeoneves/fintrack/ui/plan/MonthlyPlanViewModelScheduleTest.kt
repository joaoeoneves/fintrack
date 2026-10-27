package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Income-line schedules (basis, frequency) and the working-days override in [MonthlyPlanViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelScheduleTest : MonthlyPlanViewModelTestBase() {
    private val oct = YearMonth.of(2026, 10)

    private suspend fun kotlinx.coroutines.test.TestScope.save(
        vm: MonthlyPlanViewModel,
        schedule: IncomeSchedule,
        amount: Long = 1046L,
        month: YearMonth = oct,
    ): PlannedIncome {
        vm.onAddIncomeLine()
        advanceUntilIdle()
        vm.onSaveIncomeLine("Line", amount, schedule, month)
        advanceUntilIdle()
        return incomeRepo.lines.last()
    }

    @Test
    fun save_fixedMonthly_isTheDefaultShape() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val saved = save(vm, IncomeSchedule())

            assertEquals(IncomeBasis.FIXED, saved.basis)
            assertEquals(IncomeFrequency.MONTHLY, saved.frequency)
            assertNull(saved.monthOfYear)
            assertNull(saved.yearMonth)
            assertNull(vm.content().editor)
        }

    @Test
    fun save_perWorkingDay_keepsRateInAmountCents_andExpectedUsesWorkingDays() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val saved = save(vm, IncomeSchedule(basis = IncomeBasis.PER_WORKING_DAY))

            assertEquals(IncomeBasis.PER_WORKING_DAY, saved.basis)
            assertEquals(1046L, saved.amountCents)
            // October 2026: 22 weekdays minus the Monday 5 October holiday.
            assertEquals(1046L * 21, vm.content().summary.expectedIncomeCents)
        }

    @Test
    fun save_monthlyWithSkipMonths_keepsThem() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val saved = save(vm, IncomeSchedule(skipMonths = setOf(8, 12)))

            assertEquals(setOf(8, 12), saved.skipMonths)
        }

    @Test
    fun save_yearly_usesChosenMonth_dropsSkipMonthsAndYearMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            val schedule =
                IncomeSchedule(
                    frequency = IncomeFrequency.YEARLY,
                    monthOfYear = 7,
                    yearMonth = YearMonth.of(2030, 1),
                    skipMonths = setOf(1),
                )

            val saved = save(vm, schedule)

            assertEquals(7, saved.monthOfYear)
            assertNull(saved.yearMonth)
            assertEquals(emptySet<Int>(), saved.skipMonths)
        }

    @Test
    fun save_yearlyWithoutMonth_fallsBackToTheSelectedMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val saved = save(vm, IncomeSchedule(frequency = IncomeFrequency.YEARLY))

            assertEquals(10, saved.monthOfYear)
        }

    @Test
    fun save_once_usesYearMonth_dropsMonthOfYear_andIsAnExtra() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            val schedule =
                IncomeSchedule(frequency = IncomeFrequency.ONCE, yearMonth = oct, monthOfYear = 3)

            val saved = save(vm, schedule, amount = 30_000L)

            assertEquals(oct, saved.yearMonth)
            assertNull(saved.monthOfYear)
            assertEquals(0L, vm.content().summary.expectedIncomeCents)
            assertEquals(30_000L, vm.content().summary.extrasExpectedCents)
        }

    @Test
    fun save_onceWithoutYearMonth_fallsBackToTheSelectedMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val saved = save(vm, IncomeSchedule(frequency = IncomeFrequency.ONCE), month = YearMonth.of(2026, 3))

            assertEquals(YearMonth.of(2026, 3), saved.yearMonth)
        }

    @Test
    fun saveEveryBasisAndFrequencyCombination_producesAConsistentLine() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            for (basis in IncomeBasis.entries) {
                for (frequency in IncomeFrequency.entries) {
                    val saved = save(vm, IncomeSchedule(basis = basis, frequency = frequency))

                    assertEquals(basis, saved.basis)
                    assertEquals(frequency, saved.frequency)
                    assertEquals(frequency == IncomeFrequency.YEARLY, saved.monthOfYear != null)
                    assertEquals(frequency == IncomeFrequency.ONCE, saved.yearMonth != null)
                }
            }
            assertEquals(IncomeBasis.entries.size * IncomeFrequency.entries.size, incomeRepo.lines.size)
        }

    @Test
    fun editLine_prefillsItsSchedule() =
        runTest(testDispatcher) {
            val existing =
                PlannedIncome("a", "Bonus", 500L, 0, frequency = IncomeFrequency.YEARLY, monthOfYear = 6)
            incomeRepo.savePlannedIncome(existing)
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine("a")
            advanceUntilIdle()

            val schedule = vm.content().editor?.initialSchedule
            assertEquals(IncomeSchedule(IncomeBasis.FIXED, IncomeFrequency.YEARLY, 6), schedule)
        }

    @Test
    fun editLine_canChangeFrequency_andKeepsIdAndOrder() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", order = 4))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()

            vm.onSaveIncomeLine("a", 100L, IncomeSchedule(frequency = IncomeFrequency.ONCE, yearMonth = oct), oct)
            advanceUntilIdle()

            val saved = incomeRepo.lines.single()
            assertEquals("a", saved.id)
            assertEquals(4, saved.order)
            assertEquals(IncomeFrequency.ONCE, saved.frequency)
        }

    @Test
    fun cap_atTwentyLines_addIsNoOp_andCannotAddFlagFalse() =
        runTest(testDispatcher) {
            repeat(MAX_PLANNED_INCOME_LINES) { incomeRepo.savePlannedIncome(line("l$it", order = it)) }
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()

            assertFalse(vm.content().canAddIncomeLine)
            assertNull(vm.content().editor)
        }

    @Test
    fun cap_nineteenLines_stillAllowsAdding() =
        runTest(testDispatcher) {
            repeat(MAX_PLANNED_INCOME_LINES - 1) { incomeRepo.savePlannedIncome(line("l$it", order = it)) }
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()

            assertTrue(vm.content().canAddIncomeLine)
            assertEquals(null, vm.content().editor?.editingId)
            assertTrue(vm.content().editor != null)
        }

    @Test
    fun cap_editingExistingLineAtCap_stillWorks() =
        runTest(testDispatcher) {
            repeat(MAX_PLANNED_INCOME_LINES) { incomeRepo.savePlannedIncome(line("l$it", order = it)) }
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine("l3")
            advanceUntilIdle()

            assertEquals("l3", vm.content().editor?.editingId)
        }

    @Test
    fun workingDays_defaultsToTheCalendarCount() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val expected = WorkingDaysState(oct, days = 21, calendarDays = 21, holidays = 1, overridden = false)
            assertEquals(expected, vm.content().workingDays)
        }

    @Test
    fun setWorkingDays_overrides_andRecomputesPerDayLines() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(
                PlannedIncome("m", "Meal", 1000L, 0, basis = IncomeBasis.PER_WORKING_DAY),
            )
            val vm = viewModel()
            collect(vm)

            vm.onSetWorkingDays(oct, 18)
            advanceUntilIdle()

            val state = vm.content().workingDays
            assertEquals(18, state.days)
            assertEquals(21, state.calendarDays)
            assertTrue(state.overridden)
            assertEquals(18_000L, vm.content().summary.expectedIncomeCents)
            assertEquals(listOf(MonthSettings(oct, 18)), monthSettingsRepo.settings)
        }

    @Test
    fun setWorkingDaysNull_resetsToCalendar() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onSetWorkingDays(oct, 18)
            advanceUntilIdle()

            vm.onSetWorkingDays(oct, null)
            advanceUntilIdle()

            assertFalse(vm.content().workingDays.overridden)
            assertEquals(21, vm.content().workingDays.days)
            assertTrue(monthSettingsRepo.settings.isEmpty())
        }

    @Test
    fun setWorkingDaysZero_isAValidOverride() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onSetWorkingDays(oct, 0)
            advanceUntilIdle()

            assertEquals(0, vm.content().workingDays.days)
            assertTrue(vm.content().workingDays.overridden)
        }

    @Test
    fun override_forAnotherMonth_doesNotAffectTheSelectedOne() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onSetWorkingDays(YearMonth.of(2026, 9), 10)
            advanceUntilIdle()

            assertFalse(vm.content().workingDays.overridden)
            assertEquals(21, vm.content().workingDays.days)
        }

    @Test
    fun setWorkingDays_failure_emitsErrorEvent() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            monthSettingsRepo.nextSetError = IllegalStateException("nope")

            vm.onSetWorkingDays(oct, 10)
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_plan_save_failed), vm.errorEvent.first())
            assertFalse(vm.content().workingDays.overridden)
        }
}
