package com.joaoeoneves.fintrack.ui.plan.salary.setup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "I have ADSE" switch of the wizard sets or clears the other-deductions rate. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalarySetupViewModelAdseTest : SalarySetupViewModelTestBase() {
    @Test
    fun on_setsThreeAndAHalfPercent_withTheGivenSeparator() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            vm.onAdseChange(true, '.')
            advanceUntilIdle()
            assertEquals("3.5", vm.uiState.value.form.otherDeductions)
            assertTrue(vm.uiState.value.form.hasAdse)

            vm.onAdseChange(true, ',')
            advanceUntilIdle()
            assertEquals("3,5", vm.uiState.value.form.otherDeductions)
            assertTrue(vm.uiState.value.form.hasAdse)
        }

    @Test
    fun off_clearsTheDeduction() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            vm.onAdseChange(true, '.')
            advanceUntilIdle()
            vm.onAdseChange(false, '.')
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.form.otherDeductions)
            assertFalse(vm.uiState.value.form.hasAdse)
        }

    @Test
    fun on_overwritesAnotherTypedRate() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            val typed = vm.uiState.value.form.copy(otherDeductions = "1")
            vm.onFormChange(typed)
            advanceUntilIdle()
            vm.onAdseChange(true, '.')
            advanceUntilIdle()
            assertEquals("3.5", vm.uiState.value.form.otherDeductions)
        }
}
