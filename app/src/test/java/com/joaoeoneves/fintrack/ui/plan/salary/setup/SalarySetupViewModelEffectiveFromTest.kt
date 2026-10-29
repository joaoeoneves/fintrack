package com.joaoeoneves.fintrack.ui.plan.salary.setup

import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** The wizard's "Valid from" month and the shift hint that goes with it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalarySetupViewModelEffectiveFromTest : SalarySetupViewModelTestBase() {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun defaultMonth_isTheCurrentOne() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            assertEquals(october, vm.uiState.value.effectiveFrom)
        }

    @Test
    fun save_passesTheChosenMonthToTheNewLines() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            vm.onFormChange(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))
            vm.onEffectiveFromChange(YearMonth.of(2026, 3))
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            val net = income.lines.single { it.id == MANAGED_NET_SALARY }
            assertEquals(YearMonth.of(2026, 3), net.startMonth)
        }

    @Test
    fun save_withoutChoosingAMonth_startsInTheCurrentOne() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            vm.onFormChange(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(october, income.lines.single { it.id == MANAGED_NET_SALARY }.startMonth)
        }
}
