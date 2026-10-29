package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The wizard's navigation, validation and the form it builds; saving is in [SalarySetupViewModelSaveTest]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalarySetupViewModelTest : SalarySetupViewModelTestBase() {
    /** Picks the private sector (the first step) and leaves the wizard on the PAY step. */
    private fun TestScope.onPay(vm: SalarySetupViewModel) {
        vm.onSectorSelected(EmploymentSector.PRIVATE)
        advanceUntilIdle()
    }

    @Test
    fun existingProfile_opensAdvanced_andNeverOverwrites() =
        runTest(dispatcher) {
            profiles.saveSalaryProfile(SalaryProfile(baseGrossCents = 100_000L))

            val events = started(viewModel())

            assertEquals(listOf<SalarySetupEvent>(SalarySetupEvent.OpenAdvanced), events)
            assertEquals(100_000L, profiles.profile?.baseGrossCents)
        }

    @Test
    fun noProfile_emitsNoEvent_andStartsOnSector() =
        runTest(dispatcher) {
            val vm = viewModel()
            val events = started(vm)

            assertTrue(events.isEmpty())
            assertEquals(SalarySetupStep.SECTOR, vm.uiState.value.step)
            assertEquals(1, vm.uiState.value.stepNumber)
            assertEquals(5, vm.uiState.value.stepCount)
            assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
        }

    @Test
    fun steps_advanceInOrder_andBackReturnsThroughThem() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            onPay(vm)
            vm.onFormChange(SalaryForm(base = 100_000L))
            advanceUntilIdle()

            val forward = mutableListOf(SalarySetupStep.SECTOR, vm.uiState.value.step)
            repeat(3) {
                vm.onNext()
                advanceUntilIdle()
                forward += vm.uiState.value.step
            }
            assertEquals(SalarySetupStep.entries, forward)

            vm.onNext()
            advanceUntilIdle()
            assertEquals(SalarySetupStep.SUMMARY, vm.uiState.value.step)

            val backward = mutableListOf<SalarySetupStep>()
            while (vm.onBack()) {
                advanceUntilIdle()
                backward += vm.uiState.value.step
            }
            assertEquals(
                listOf(SalarySetupStep.MEAL, SalarySetupStep.SUBSIDIES, SalarySetupStep.PAY, SalarySetupStep.SECTOR),
                backward,
            )
        }

    @Test
    fun onBack_onSector_returnsFalse_andStays() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)

            assertFalse(vm.onBack())
            assertEquals(SalarySetupStep.SECTOR, vm.uiState.value.step)
        }

    @Test
    fun backKeepsWhatWasTyped() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            onPay(vm)
            vm.onFormChange(SalaryForm(base = 100_000L, meal = 800L))
            advanceUntilIdle()
            vm.onNext()
            advanceUntilIdle()
            assertEquals(SalarySetupStep.SUBSIDIES, vm.uiState.value.step)

            vm.onBack()
            advanceUntilIdle()

            assertEquals(100_000L, vm.uiState.value.form.base)
            assertEquals(800L, vm.uiState.value.form.meal)
        }

    @Test
    fun stepSurvivesRecreation_viaSavedState() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            started(first)
            first.onSectorSelected(EmploymentSector.PRIVATE)
            advanceUntilIdle()
            first.onFormChange(SalaryForm(base = 100_000L))
            advanceUntilIdle()
            first.onNext()
            advanceUntilIdle()

            val second = viewModel(handle)
            started(second)

            assertEquals(SalarySetupStep.SUBSIDIES, second.uiState.value.step)
        }

    @Test
    fun canContinue_needsAPositiveAmountOnPay() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            onPay(vm)
            assertFalse(vm.uiState.value.canContinue)

            vm.onFormChange(SalaryForm(base = 0L))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canContinue)

            vm.onFormChange(SalaryForm(base = 1L))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun canContinue_netModeReadsTheNetAmount() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            onPay(vm)

            vm.onFormChange(SalaryForm(mode = SalaryInputMode.NET, base = 100_000L))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canContinue)

            vm.onFormChange(SalaryForm(mode = SalaryInputMode.NET, netMonthly = 90_000L))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun invalidField_blocksContinue_andHidesTheBreakdown() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            onPay(vm)

            vm.onFormChange(SalaryForm(base = 100_000L, irsBase = "abc"))
            advanceUntilIdle()
            vm.onNext()
            advanceUntilIdle()

            assertEquals(setOf(SalaryField.IRS_BASE), vm.uiState.value.invalid)
            assertFalse(vm.uiState.value.canContinue)
            assertNull(vm.uiState.value.breakdown)
            assertEquals(SalarySetupStep.PAY, vm.uiState.value.step)
        }

    @Test
    fun onNext_withoutAmount_doesNotMove() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            onPay(vm)

            vm.onNext()
            advanceUntilIdle()

            assertEquals(SalarySetupStep.PAY, vm.uiState.value.step)
        }

    @Test
    fun breakdown_andWorkingDays_followTheFormAndClock() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            val form = SalaryForm(base = 250_000L, spreadPpm = SPREAD_FULL_PPM, irsBase = "15", meal = 900L)

            vm.onFormChange(form)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNotNull(state.breakdown)
            assertEquals(900L, state.breakdown?.mealRatePerDayCents)
            assertEquals(workingDaysIn(YearMonth.of(2026, 10)), state.workingDays)
        }

    @Test
    fun netTwelfths_dropsTheLumpSums() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            vm.onFormChange(
                netForm(),
            )
            vm.onNetLumpsChange(true)
            advanceUntilIdle()
            assertEquals(50_000L, vm.uiState.value.form.netHoliday)

            vm.onNetLumpsChange(false)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.netLumps)
            assertEquals(0L, vm.uiState.value.form.netHoliday)
            assertEquals(0L, vm.uiState.value.form.netChristmas)
        }

    @Test
    fun netLumpsChoice_survivesRecreation() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            started(first)
            first.onNetLumpsChange(true)
            advanceUntilIdle()

            val second = viewModel(handle)
            started(second)

            assertTrue(second.uiState.value.netLumps)
        }
}
