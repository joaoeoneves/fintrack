package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/** The wizard's "Valid from" month and the shift hint that goes with it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalarySetupViewModelEffectiveFromTest : SalarySetupViewModelTestBase() {
    private class Shift(
        private val value: Boolean?,
    ) : PlanSettingsRepository {
        override fun observeSalaryPaysNextMonth(): Flow<Boolean?> = flowOf(value)

        override suspend fun setSalaryPaysNextMonth(enabled: Boolean) = Unit
    }

    private fun shiftVm(value: Boolean?) =
        SalarySetupViewModel(
            SavedStateHandle(),
            profiles,
            ManageSalaryProfile(profiles, income),
            income,
            clock,
            Shift(value),
        )

    private val october = YearMonth.of(2026, 10)

    @Test
    fun defaultMonth_isTheCurrentOne() =
        runTest(dispatcher) {
            val vm = viewModel()
            started(vm)
            assertEquals(october, vm.uiState.value.effectiveFrom)
        }

    @Test
    fun shiftSource_isThePreviousMonth_whenPreferenceNullOrTrue() =
        runTest(dispatcher) {
            for (pref in listOf<Boolean?>(null, true)) {
                val vm = shiftVm(pref)
                started(vm)
                assertEquals(YearMonth.of(2026, 9), vm.uiState.value.shiftSource)
            }
        }

    @Test
    fun shiftSource_isNull_whenShiftIsOff() =
        runTest(dispatcher) {
            val vm = shiftVm(false)
            started(vm)
            assertNull(vm.uiState.value.shiftSource)
        }

    @Test
    fun shiftSource_followsTheChosenMonth() =
        runTest(dispatcher) {
            val vm = shiftVm(true)
            started(vm)
            vm.onEffectiveFromChange(YearMonth.of(2027, 1))
            advanceUntilIdle()
            assertEquals(YearMonth.of(2027, 1), vm.uiState.value.effectiveFrom)
            assertEquals(YearMonth.of(2026, 12), vm.uiState.value.shiftSource)
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
