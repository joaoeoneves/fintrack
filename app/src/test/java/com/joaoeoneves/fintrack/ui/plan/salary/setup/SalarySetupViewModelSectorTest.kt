package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SPREAD_NONE_PPM
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sector step of the wizard: presets, state restore, the pay-day answer and what is saved with it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalarySetupViewModelSectorTest : SalarySetupViewModelTestBase() {
    private suspend fun TestScope.toSummary(
        vm: SalarySetupViewModel,
        sector: EmploymentSector,
    ) {
        vm.onSectorSelected(sector)
        advanceUntilIdle()
        vm.onFormChange(
            vm.uiState.value.form
                .copy(base = 100_000L),
        )
        advanceUntilIdle()
        repeat(3) {
            vm.onNext()
            advanceUntilIdle()
        }
    }

    @Test
    fun selectingPublic_appliesItsPresets_andMovesToPay() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)

            vm.onSectorSelected(EmploymentSector.PUBLIC)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SalarySetupStep.PAY, state.step)
            assertEquals(EmploymentSector.PUBLIC, state.sector)
            assertEquals(SPREAD_NONE_PPM, state.form.spreadPpm)
            assertEquals(listOf(6, 11), state.form.subsidyMonths)
            assertEquals(MealPayment.WITH_SALARY, state.form.mealPayment)
            assertEquals(MealAnswer.WITH_SALARY, state.mealAnswer)
            assertEquals(PayDayDraft(fixed = true), state.payDay)
        }

    @Test
    fun selectingPrivate_keepsTodaysDefaults() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)

            vm.onSectorSelected(EmploymentSector.PRIVATE)
            advanceUntilIdle()

            assertEquals(MealAnswer.CARD, vm.uiState.value.mealAnswer)
            assertEquals(PayDayDraft(), vm.uiState.value.payDay)
            assertEquals(listOf(6, 12), vm.uiState.value.form.subsidyMonths)
        }

    @Test
    fun reselectingTheSameSector_keepsWhatWasTyped() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            vm.onSectorSelected(EmploymentSector.PRIVATE)
            vm.onFormChange(
                vm.uiState.value.form
                    .copy(base = 123_00L),
            )
            advanceUntilIdle()
            vm.onBack()
            vm.onSectorSelected(EmploymentSector.PRIVATE)
            advanceUntilIdle()

            assertEquals(123_00L, vm.uiState.value.form.base)
        }

    @Test
    fun sectorAndStepSurviveRecreation() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            started(first)
            first.onSectorSelected(EmploymentSector.PUBLIC)
            advanceUntilIdle()

            val second = viewModel(handle)
            started(second)

            assertEquals(SalarySetupStep.PAY, second.uiState.value.step)
            assertEquals(EmploymentSector.PUBLIC, second.uiState.value.sector)
            assertEquals(MealPayment.WITH_SALARY, second.uiState.value.form.mealPayment)
            assertEquals(PayDayDraft(fixed = true), second.uiState.value.payDay)
        }

    @Test
    fun aStoredStepWithoutASector_startsOver() =
        runTest(dispatcher) {
            val vm = viewModel(SavedStateHandle(mapOf("step" to "MEAL")))
            started(vm)

            assertEquals(SalarySetupStep.SECTOR, vm.uiState.value.step)
        }

    @Test
    fun summary_publicNeedsAValidFixedDay() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            toSummary(vm, EmploymentSector.PUBLIC)
            assertEquals(SalarySetupStep.SUMMARY, vm.uiState.value.step)
            assertFalse(vm.uiState.value.canContinue)

            vm.onPayDayChange(PayDayDraft(fixed = true, dayText = "32"))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canContinue)

            vm.onPayDayChange(PayDayDraft(fixed = true, dayText = "2x5"))
            advanceUntilIdle()
            assertEquals("25", vm.uiState.value.payDay.dayText)
            assertTrue(vm.uiState.value.canContinue)

            vm.onPayDayChange(PayDayDraft(fixed = false))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun save_withAnEmptyFixedDay_doesNothing() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            toSummary(vm, EmploymentSector.PUBLIC)

            vm.onSave(labels)
            advanceUntilIdle()

            assertNull(profiles.profile)
        }

    @Test
    fun save_fixedDay_goesOnEveryManagedLine_withTheEarlierFlag() =
        runTest(dispatcher) {
            income.savePlannedIncome(PlannedIncome("m1", "Side gig", 5_000L, 9))
            val vm = viewModel()
            started(vm)
            toSummary(vm, EmploymentSector.PRIVATE)
            vm.onFormChange(
                vm.uiState.value.form
                    .copy(meal = 900L),
            )
            vm.onPayDayChange(PayDayDraft(fixed = true, dayText = "25", earlierIfClosed = true))
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            val managed = income.lines.filter { it.managedKey != null }
            assertTrue(managed.size >= 2)
            assertTrue(managed.all { it.payDay == 25 && it.payDayEarlierIfClosed })
            val manual = income.lines.single { it.id == "m1" }
            assertNull(manual.payDay)
            assertFalse(manual.payDayEarlierIfClosed)
        }

    @Test
    fun save_lastWorkingDay_writesNoPayDay() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            toSummary(vm, EmploymentSector.PRIVATE)
            vm.onFormChange(
                vm.uiState.value.form
                    .copy(meal = 900L),
            )
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            val managed = income.lines.filter { it.managedKey != null }
            assertTrue(managed.isNotEmpty())
            assertTrue(managed.all { it.payDay == null && !it.payDayEarlierIfClosed })
        }

    @Test
    fun save_keepsTheSectorOnTheProfile() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            toSummary(vm, EmploymentSector.PRIVATE)
            vm.onFormChange(SalaryForm(base = 100_000L, sector = EmploymentSector.PRIVATE).withAdse(true))
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(EmploymentSector.PRIVATE, profiles.profile?.sector)
            assertEquals(35_000, profiles.profile?.otherDeductionsPpm)
        }
}
